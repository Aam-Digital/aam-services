package com.aamdigital.aambackendservice.reporting.report.core

import com.aamdigital.aambackendservice.common.cache.LazySnapshot
import com.aamdigital.aambackendservice.common.domain.DomainReference
import org.slf4j.LoggerFactory

/**
 * CouchDB-backed in-memory implementation of [ReportConfigCache].
 *
 * The first read after startup, and the first read after each [markDirty], fetches all SQL
 * `ReportConfig:*` documents and runs the entity analysis once (see [LazySnapshot]). There is
 * deliberately no startup warmup — it would only save the latency of a single CouchDB request on
 * the first change, while adding a warmup thread that races that same first change. A CouchDB
 * outage therefore surfaces exactly as it does today, just on far fewer requests.
 */
class DefaultReportConfigCache(
    private val reportStorage: ReportStorage,
    private val reportQueryAnalyser: ReportQueryAnalyser
) : ReportConfigCache {
    private val logger = LoggerFactory.getLogger(javaClass)

    private val entries = LazySnapshot { loadEntries() }

    override fun findReportsForEntityType(entityType: String): List<DomainReference> =
        entries
            .get()
            .filter { entry -> entry.affectedEntityTypes.contains(entityType) }
            .map { entry -> DomainReference(entry.reportId) }

    override fun markDirty() {
        entries.invalidate()
        logger.debug("Report config cache marked stale, will reload on next read")
    }

    private fun loadEntries(): List<ReportConfigCacheEntry> =
        reportStorage
            .fetchAllReports("sql")
            .map { report ->
                ReportConfigCacheEntry(
                    reportId = report.id,
                    affectedEntityTypes = reportQueryAnalyser.getAffectedEntities(report).toSet()
                )
            }.also { loaded -> logger.debug("Loaded {} report definitions into memory cache", loaded.size) }
}
