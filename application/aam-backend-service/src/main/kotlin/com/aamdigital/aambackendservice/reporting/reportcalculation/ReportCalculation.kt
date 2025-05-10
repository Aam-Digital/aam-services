package com.aamdigital.aambackendservice.reporting.reportcalculation

import com.aamdigital.aambackendservice.couchdb.dto.AttachmentMetaData
import com.aamdigital.aambackendservice.domain.DomainReference

// todo remove connection to couchdb.* from domain model
data class ReportCalculation(
    val id: String,
    val report: DomainReference,
    var status: ReportCalculationStatus,
    var errorDetails: String? = null,
    var calculationStarted: String? = null,
    var calculationCompleted: String? = null,
    var args: MutableMap<String, String> = mutableMapOf(),
    val attachments: MutableMap<String, AttachmentMetaData> = mutableMapOf(),
) {
    fun setStatus(status: ReportCalculationStatus): ReportCalculation {
        this.status = status
        return this
    }

    fun setStartDate(startDate: String?): ReportCalculation {
        this.calculationStarted = startDate
        return this
    }

    fun setErrorDetails(errorDetails: String?): ReportCalculation {
        this.errorDetails = errorDetails
        return this
    }

    fun setEndDate(endDate: String?): ReportCalculation {
        this.calculationCompleted = endDate
        return this
    }
}


enum class ReportCalculationStatus {
    PENDING, RUNNING, FINISHED_SUCCESS, FINISHED_ERROR
}
