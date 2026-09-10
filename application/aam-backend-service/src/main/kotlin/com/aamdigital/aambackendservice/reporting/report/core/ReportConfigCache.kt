package com.aamdigital.aambackendservice.reporting.report.core

import com.aamdigital.aambackendservice.common.domain.DomainReference

/**
 * Cached analysis result for one `ReportConfig:` document.
 *
 * [affectedEntityTypes] is the output of [ReportQueryAnalyser.getAffectedEntities], computed once
 * per report definition instead of once per document change.
 */
data class ReportConfigCacheEntry(
    val reportId: String,
    val affectedEntityTypes: Set<String>
)

/**
 * Read and refresh contract for report definition caching.
 *
 * Exists so that automatic change detection does not have to re-read every `ReportConfig:`
 * document and re-run the SQL entity-extraction regex for every single document change.
 * Implementations keep the in-memory analysis synchronized with `ReportConfig:*` document
 * changes, which reach the reporting module because the `app` database is the one entry in
 * `database-change-detection.included-databases`.
 */
interface ReportConfigCache {
    /**
     * All reports whose SQL reads the given entity type. Purely in-memory unless the cache is
     * stale, in which case it loads first and propagates the same failures
     * [ReportStorage.fetchAllReports] would.
     */
    fun findReportsForEntityType(entityType: String): List<DomainReference>

    /** Reloads and replaces the whole cache. */
    fun refreshAll()

    /**
     * Marks the cache stale without doing any I/O, so the next read reloads. Never throws, so it
     * is safe to call from inside change-event handling.
     */
    fun markDirty()
}
