package com.aamdigital.aambackendservice.reporting.report.storage

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.JsonNode

/**
 * Represents a canonical CouchDB ReportConfig: entity (mode=sql).
 *
 * @param id couchdb _id
 * @param rev couchdb _rev of this document
 * @param title human-readable title of the Report: a plain string, or a map of locale id to text
 *     if it is configured in several languages (see [resolveReportTitle])
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
    val title: JsonNode? = null,
    val mode: String,
    val transformations: Map<String, List<String>>? = null,
    // nullable elements: without StrictNullChecks, Jackson 2 lets a JSON null into the list anyway
    val reportDefinition: List<ReportDefinitionDto?> = emptyList()
)

/**
 * One query (or group) making up a report.
 * This should either have `query` or `groupTitle` + `items` properties, not both.
 */
data class ReportDefinitionDto(
    val query: String?,
    val groupTitle: String?,
    val items: List<ReportDefinitionDto?>?
)
