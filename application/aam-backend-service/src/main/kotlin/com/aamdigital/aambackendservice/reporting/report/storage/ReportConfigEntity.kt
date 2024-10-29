package com.aamdigital.aambackendservice.reporting.report.storage

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Represents an CouchDB ReportConfig: entity in version 2
 * Just relevant for ReportConfigs with mode=sql
 *
 * @param id couchdb _id
 * @param rev couchdb _rev of this document
 * @param title human-readable title of the Report
 * @param mode can be sql or something else. just sql values are supported right now
 * @param version schema version of a ReportConfig, used to apply different business logic
 * @param transformations configures wich DataTransformation will be applied on wich variable
 *                          (e.g) name -> [UPPERCASE, REVERSE]
 * @param reportDefinition List of ReportDefinitions
 */
data class ReportConfigEntity(
    @JsonProperty("_id")
    val id: String,
    @JsonProperty("_rev")
    val rev: String,
    val title: String,
    val mode: String,
    val version: Int,
    val transformations: Map<String, List<String>>,
    val reportDefinition: List<ReportDefinitionDto>,
)

/**
 * One query (or group) making up a report.
 * This should either have `query` or `groupTitle` + `items` properties, not both.
 */
data class ReportDefinitionDto(
    val query: String?,
    val groupTitle: String?,
    val items: List<ReportDefinitionDto>?,
)
