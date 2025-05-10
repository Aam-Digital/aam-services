package com.aamdigital.aambackendservice.reporting.reportcalculation.storage

import com.aamdigital.aambackendservice.common.couchdb.dto.AttachmentMetaData
import com.aamdigital.aambackendservice.common.domain.DomainReference
import com.aamdigital.aambackendservice.reporting.reportcalculation.ReportCalculationStatus
import com.fasterxml.jackson.annotation.JsonProperty

data class ReportCalculationEntity(
    @JsonProperty("_id")
    val id: String,
    val report: DomainReference,
    var status: ReportCalculationStatus,
    var errorDetails: String? = null,
    var calculationStarted: String? = null,
    var calculationCompleted: String? = null,
    var args: MutableMap<String, String> = mutableMapOf(),
    @JsonProperty("_attachments")
    val attachments: MutableMap<String, AttachmentMetaData> = mutableMapOf(),
)
