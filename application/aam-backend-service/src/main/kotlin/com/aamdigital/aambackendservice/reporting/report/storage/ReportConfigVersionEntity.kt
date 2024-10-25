package com.aamdigital.aambackendservice.reporting.report.storage

/**
 * Load just the version of a ReportConfig.
 *
 * @param version schema version of a ReportConfig, used to apply different business logic
 */
data class ReportConfigVersionEntity(
    val version: Int = 1,
)
