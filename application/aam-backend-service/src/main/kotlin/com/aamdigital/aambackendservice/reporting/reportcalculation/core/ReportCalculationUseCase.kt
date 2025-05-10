package com.aamdigital.aambackendservice.reporting.reportcalculation.core

import com.aamdigital.aambackendservice.common.domain.DomainUseCase
import com.aamdigital.aambackendservice.common.domain.UseCaseData
import com.aamdigital.aambackendservice.common.domain.UseCaseRequest
import com.aamdigital.aambackendservice.common.error.AamErrorCode
import com.aamdigital.aambackendservice.reporting.reportcalculation.ReportCalculation

data class ReportCalculationRequest(
    val reportCalculationId: String,
) : UseCaseRequest

data class ReportCalculationData(
    val reportCalculation: ReportCalculation,
) : UseCaseData

enum class ReportCalculationError : AamErrorCode {
    UNEXPECTED_ERROR,
    REPORT_CALCULATION_NOT_FOUND,
    REPORT_NOT_FOUND,
    UNSUPPORTED_REPORT_VERSION,
    PARSING_ERROR,
    IO_ERROR,
}

abstract class ReportCalculationUseCase : DomainUseCase<ReportCalculationRequest, ReportCalculationData>()
