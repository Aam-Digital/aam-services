package com.aamdigital.aambackendservice.reporting.report.core

import com.aamdigital.aambackendservice.common.domain.DomainReference
import org.slf4j.LoggerFactory

/**
 * CouchDB-backed in-memory implementation of [ReportConfigCache].
 *
 * Loading is lazy rather than eager: the first read after startup, and the first read after each
 * [markDirty], fetches all SQL `ReportConfig:*` documents and runs the entity analysis once. There
 * is deliberately no startup warmup — it would only save the latency of a single CouchDB request
 * on the first change, while adding a warmup thread that races that same first change. A CouchDB
 * outage therefore surfaces exactly as it does today, just on far fewer requests.
 */
class DefaultReportConfigCache(
    private val reportStorage: ReportStorage,
    private val reportQueryAnalyser: ReportQueryAnalyser
) : ReportConfigCache {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val cacheLock = Any()

    private var entries: List<ReportConfigCacheEntry> = emptyList()
    private var loaded: Boolean = false

    override fun findReportsForEntityType(entityType: String): List<DomainReference> =
        synchronized(cacheLock) {
            if (!loaded) {
                refreshAll()
            }

            entries
                .filter { entry -> entry.affectedEntityTypes.contains(entityType) }
                .map { entry -> DomainReference(entry.reportId) }
        }

    /**
     * Reloads from CouchDB. The fetch happens while holding [cacheLock] by design: the only reader
     * is the single-threaded reporting change-detection path, so there is no concurrency to trade
     * away, and the lock rules out two concurrent `_all_docs` requests for the same reload.
     */
    override fun refreshAll() {
        synchronized(cacheLock) {
            entries =
                reportStorage
                    .fetchAllReports("sql")
                    .map { report ->
                        ReportConfigCacheEntry(
                            reportId = report.id,
                            affectedEntityTypes = reportQueryAnalyser.getAffectedEntities(report).toSet()
                        )
                    }
            loaded = true

            logger.debug("Loaded {} report definitions into memory cache", entries.size)
        }
    }

    override fun markDirty() {
        synchronized(cacheLock) {
            loaded = false
        }
        logger.debug("Report config cache marked stale, will reload on next read")
    }
}
