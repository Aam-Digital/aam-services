package com.aamdigital.aambackendservice.reporting.reportcalculation.core

import com.aamdigital.aambackendservice.common.domain.DomainReference
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

data class CreateReportCalculationRequest(
    val report: DomainReference,
    val args: MutableMap<String, String>,
    val fromAutomaticChangeDetection: Boolean = false,
)

@JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION)
@JsonSubTypes(
    JsonSubTypes.Type(value = CreateReportCalculationResult.Success::class),
    JsonSubTypes.Type(value = CreateReportCalculationResult.Failure::class),
)
sealed class CreateReportCalculationResult {
    data class Success(
        val calculation: DomainReference,
    ) : CreateReportCalculationResult()

    data class Failure(
        val errorCode: ErrorCode,
        val errorMessage: String = "",
        val cause: Throwable? = null
    ) : CreateReportCalculationResult()

    enum class ErrorCode {
        INTERNAL_SERVER_ERROR
    }
}

interface CreateReportCalculationUseCase {
    fun createReportCalculation(request: CreateReportCalculationRequest): CreateReportCalculationResult
}
