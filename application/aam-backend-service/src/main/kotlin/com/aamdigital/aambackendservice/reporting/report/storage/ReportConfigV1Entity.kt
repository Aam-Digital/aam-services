package com.aamdigital.aambackendservice.reporting.report.storage

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Represents an CouchDB ReportConfig: entity in version 1
 * Just relevant for ReportConfigs with mode=sql
 *
 * Replaced by: ReportConfigEntity
 */
data class ReportConfigV1Entity(
    @JsonProperty("_id")
    val id: String,
    @JsonProperty("_rev")
    val rev: String,
    val title: String,
    val version: Int = 1,
    val mode: String = "unknown",
    val aggregationDefinition: String = "",
    val neededArgs: List<String> = emptyList(),
    val created: EditAtBy?,
    val updated: EditAtBy?,
)

data class EditAtBy(
    val at: String,
    val by: String,
)
