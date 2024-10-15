package com.aamdigital.aambackendservice.reporting.reportcalculation.core

import com.aamdigital.aambackendservice.domain.DomainReference
import com.aamdigital.aambackendservice.reporting.domain.ReportCalculation
import com.aamdigital.aambackendservice.reporting.domain.ReportCalculationStatus
import com.aamdigital.aambackendservice.reporting.report.core.ReportingStorage
import org.springframework.stereotype.Service
import java.util.*

@Service
class DefaultCreateReportCalculationUseCase(
    private val reportingStorage: ReportingStorage,
) : CreateReportCalculationUseCase {

    override fun createReportCalculation(request: CreateReportCalculationRequest): CreateReportCalculationResult {
        val calculation = ReportCalculation(
            id = "ReportCalculation:${UUID.randomUUID()}",
            report = request.report,
            status = ReportCalculationStatus.PENDING,
            args = request.args
        )

        return try {
            val reportCalculations = reportingStorage.fetchCalculations(request.report)

            val i = reportCalculations.filter { reportCalculation ->
                reportCalculation.status == ReportCalculationStatus.PENDING &&
                        reportCalculation.args == calculation.args
            }

            if (i.isNotEmpty()) {
                CreateReportCalculationResult.Success(
                    DomainReference(
                        id = i.first().id
                    )
                )
            } else {
                handleResponse(reportingStorage.storeCalculation(calculation))
            }
        } catch (ex: Exception) {
            handleError(ex)
        }
    }

    private fun handleResponse(reportCalculation: ReportCalculation): CreateReportCalculationResult {
        return CreateReportCalculationResult.Success(
            DomainReference(id = reportCalculation.id)
        )
    }

    private fun handleError(it: Throwable): CreateReportCalculationResult {
        return CreateReportCalculationResult.Failure(
            errorCode = CreateReportCalculationResult.ErrorCode.INTERNAL_SERVER_ERROR, cause = it
        )
    }
}
