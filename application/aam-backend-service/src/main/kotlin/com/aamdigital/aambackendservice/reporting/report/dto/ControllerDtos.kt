package com.aamdigital.aambackendservice.reporting.report.dto

import com.aamdigital.aambackendservice.couchdb.dto.CouchDbRow
import com.aamdigital.aambackendservice.reporting.domain.ReportSchema
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * This is the interface shared to external users of the API endpoints.
 */
data class ReportDto(
    val id: String,
    val name: String,
    val schema: ReportSchema?,
    val calculationPending: Boolean
)

data class ReportDoc(
    @JsonProperty("_id")
    val id: String,
    @JsonProperty("_rev")
    val rev: String,
    val title: String,
    val mode: String,
    val aggregationDefinition: String,
    val created: EditAtBy?,
    val updated: EditAtBy?,
)

data class EditAtBy(
    val at: String,
    val by: String,
)

data class FetchReportsResponse(
    @JsonProperty("total_rows")
    val totalRows: Int,
    val offset: Int,
    val rows: List<CouchDbRow<ReportDoc>>
)
