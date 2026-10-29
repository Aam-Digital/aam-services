package com.aamdigital.aambackendservice.reporting.report.storage

import com.aamdigital.aambackendservice.common.domain.TranslatableTextDeserializer
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonDeserialize

/**
 * Represents a canonical CouchDB ReportConfig: entity (mode=sql).
 *
 * @param id couchdb _id
 * @param rev couchdb _rev of this document
 * @param title human-readable title of the Report (one language, if configured as translations)
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
    @JsonDeserialize(using = TranslatableTextDeserializer::class)
    val title: String,
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
