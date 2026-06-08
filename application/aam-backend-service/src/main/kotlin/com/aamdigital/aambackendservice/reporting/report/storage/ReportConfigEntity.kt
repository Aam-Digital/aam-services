package com.aamdigital.aambackendservice.reporting.report.storage

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Represents a canonical CouchDB ReportConfig: entity (mode=sql).
 *
 * @param id couchdb _id
 * @param rev couchdb _rev of this document
 * @param title human-readable title of the Report
 * @param mode report mode; only "sql" is supported
 * @param transformations optional arg transformations, e.g. startDate → [SQL_FROM_DATE]
 * @param reportDefinition list of queries and/or groups making up the report
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class ReportConfigEntity(
    @JsonProperty("_id")
    val id: String,
    @JsonProperty("_rev")
    val rev: String,
    val title: String,
    val mode: String,
    val transformations: Map<String, List<String>>? = null,
    val reportDefinition: List<ReportDefinitionDto> = emptyList()
)

/**
 * One query (or group) making up a report.
 * This should either have `query` or `groupTitle` + `items` properties, not both.
 */
data class ReportDefinitionDto(
    val query: String?,
    val groupTitle: String?,
    val items: List<ReportDefinitionDto>?
)
