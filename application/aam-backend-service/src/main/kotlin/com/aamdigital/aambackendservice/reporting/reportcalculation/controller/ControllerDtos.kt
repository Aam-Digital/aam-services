package com.aamdigital.aambackendservice.reporting.reportcalculation.controller

import com.aamdigital.aambackendservice.common.domain.DomainReference
import com.aamdigital.aambackendservice.reporting.reportcalculation.ReportCalculationStatus

/**
 * This is the interface shared to external users of the API endpoints.
 */
data class ReportCalculationDto(
    val id: String,
    val report: DomainReference,
    var status: ReportCalculationStatus,
    var startDate: String? = null,
    var endDate: String? = null,
    var args: Map<String, String>,
    var data: ReportCalculationData?,
    var errorDetails: String? = null,
)

data class ReportCalculationData(
    val contentType: String,
    val hash: String,
    val length: Number,
)

/**
 * used by /data endpoint in ReportCalculationController, but response is build dynamically
 * Just kept for backwarts compatibility
 */
data class ReportDataDto(
    val id: String,
    val report: DomainReference,
    val calculation: DomainReference,
    val data: String
)
