package com.aamdigital.aambackendservice.reporting.reportcalculation.dto

import com.aamdigital.aambackendservice.domain.DomainReference
import com.aamdigital.aambackendservice.reporting.domain.ReportCalculationOutcome
import com.aamdigital.aambackendservice.reporting.domain.ReportCalculationParams
import com.aamdigital.aambackendservice.reporting.domain.ReportCalculationStatus
import com.fasterxml.jackson.databind.ObjectMapper
import java.security.MessageDigest

/**
 * This is the interface shared to external users of the API endpoints.
 */
data class ReportCalculationDto(
    val id: String,
    val report: DomainReference,
    var status: ReportCalculationStatus,
    var startDate: String? = null,
    var endDate: String? = null,
    var params: ReportCalculationParams?,
    var outcome: ReportCalculationOutcome? = null,
)

data class ReportDataDto(
    val id: String,
    val report: DomainReference,
    val calculation: DomainReference,
    var data: List<*>,
) {
    @OptIn(ExperimentalStdlibApi::class)
    fun getDataHash(): String {
        val mapper = ObjectMapper()
        val md = MessageDigest.getInstance("SHA-256")
        val input = mapper.writeValueAsString(data).toByteArray()
        val bytes = md.digest(input)
        return bytes.toHexString()
    }
}
