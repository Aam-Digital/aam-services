package com.aamdigital.aambackendservice.reporting.domain

import com.aamdigital.aambackendservice.domain.DomainReference
import com.fasterxml.jackson.annotation.JsonProperty

data class ReportData(
    @JsonProperty("_id")
    val id: String,
    val report: DomainReference,
    val calculation: DomainReference,
)
