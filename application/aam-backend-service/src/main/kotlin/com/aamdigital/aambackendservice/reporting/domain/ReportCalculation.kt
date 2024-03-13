package com.aamdigital.aambackendservice.reporting.domain

import com.aamdigital.aambackendservice.domain.DomainReference
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

@JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION)
@JsonSubTypes(
    JsonSubTypes.Type(value = ReportCalculationOutcome.Success::class),
    JsonSubTypes.Type(value = ReportCalculationOutcome.Failure::class),
)
sealed class ReportCalculationOutcome {
    data class Success(
        @JsonProperty("result_hash")
        val resultHash: String,
    ) : ReportCalculationOutcome()

    data class Failure(
        val errorCode: String,
        val errorMessage: String,
    ) : ReportCalculationOutcome()
}

data class ReportCalculation(
    val id: String,
    val report: DomainReference,
    var status: ReportCalculationStatus,
    var startDate: String? = null,
    var endDate: String? = null,
    var outcome: ReportCalculationOutcome? = null,
) {
    fun setStatus(status: ReportCalculationStatus): ReportCalculation {
        this.status = status
        return this
    }

    fun setStartDate(startDate: String?): ReportCalculation {
        this.startDate = startDate
        return this
    }

    fun setEndDate(endDate: String?): ReportCalculation {
        this.endDate = endDate
        return this
    }

    fun setOutcome(outcome: ReportCalculationOutcome?): ReportCalculation {
        this.outcome = outcome
        return this
    }
}


enum class ReportCalculationStatus {
    PENDING, RUNNING, FINISHED_SUCCESS, FINISHED_ERROR
}
