package com.aamdigital.aambackendservice.reporting.reportcalculation.core

import com.aamdigital.aambackendservice.domain.DomainReference
import com.aamdigital.aambackendservice.reporting.domain.ReportCalculation
import com.aamdigital.aambackendservice.reporting.domain.ReportCalculationStatus
import com.aamdigital.aambackendservice.reporting.report.core.ReportingStorage
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.util.*

@Service
class DefaultCreateReportCalculationUseCase(
    private val reportingStorage: ReportingStorage,
) : CreateReportCalculationUseCase {

    override fun createReportCalculation(request: CreateReportCalculationRequest): Mono<CreateReportCalculationResult> {
        val calculation = ReportCalculation(
            id = "ReportCalculation:${UUID.randomUUID()}",
            report = request.report,
            status = ReportCalculationStatus.PENDING,
            args = request.args
        )

        return reportingStorage.fetchCalculations(request.report)
            .flatMap { reportCalculations ->
                val i = reportCalculations.filter { reportCalculation ->
                    reportCalculation.status == ReportCalculationStatus.PENDING &&
                            reportCalculation.args == calculation.args
                }

                if (i.isNotEmpty()) {
                    Mono.just(
                        CreateReportCalculationResult.Success(
                            DomainReference(
                                id = i.first().id
                            )
                        )
                    )
                } else {
                    reportingStorage.storeCalculation(calculation).map {
                        handleResponse(it)
                    }
                }
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
                errorCode = CreateReportCalculationResult.ErrorCode.INTERNAL_SERVER_ERROR, cause = it
            )
        )
    }
}
