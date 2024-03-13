package com.aamdigital.aambackendservice.reporting.report.calculation.core

import com.aamdigital.aambackendservice.domain.DomainReference
import com.aamdigital.aambackendservice.domain.ReportCalculation
import com.aamdigital.aambackendservice.domain.ReportCalculationStatus
import com.aamdigital.aambackendservice.reporting.report.core.ReportingStorage
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.util.*

@Service
class DefaultCreateReportCalculationUseCase(
    private val reportingStorage: ReportingStorage,
) : CreateReportCalculationUseCase {
    override fun startReportCalculation(request: CreateReportCalculationRequest): Mono<CreateReportCalculationResult> {
        val calculation = ReportCalculation(
            id = "ReportCalculation:${UUID.randomUUID()}",
            report = request.report,
            status = ReportCalculationStatus.PENDING,
        )

        return reportingStorage.storeCalculation(calculation)
            .map {
                handleResponse(it)
            }
            .onErrorResume {
                handleError(it)
            }
    }

    private fun handleResponse(reportCalculation: ReportCalculation): CreateReportCalculationResult {
        return CreateReportCalculationResult.Success(
            DomainReference(id = reportCalculation.id)
        )
    }

    private fun handleError(it: Throwable): Mono<CreateReportCalculationResult> {
        return Mono.just(
            CreateReportCalculationResult.Failure(
                errorCode = CreateReportCalculationResult.ErrorCode.INTERNAL_SERVER_ERROR,
                cause = it
            )
        )
    }
}
