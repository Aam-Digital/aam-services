package com.aamdigital.aambackendservice.reporting.reportcalculation.usecase

import com.aamdigital.aambackendservice.domain.DomainReference
import com.aamdigital.aambackendservice.reporting.domain.ReportCalculation
import com.aamdigital.aambackendservice.reporting.domain.ReportCalculationStatus
import com.aamdigital.aambackendservice.reporting.domain.event.ReportCalculationEvent
import com.aamdigital.aambackendservice.reporting.reportcalculation.core.CreateReportCalculationRequest
import com.aamdigital.aambackendservice.reporting.reportcalculation.core.CreateReportCalculationResult
import com.aamdigital.aambackendservice.reporting.reportcalculation.core.CreateReportCalculationUseCase
import com.aamdigital.aambackendservice.reporting.reportcalculation.core.ReportCalculationStorage
import com.aamdigital.aambackendservice.reporting.reportcalculation.queue.RabbitMqReportCalculationEventPublisher
import java.util.*

// todo: migrate to DomainUseCase
class DefaultCreateReportCalculationUseCase(
    private val reportCalculationStorage: ReportCalculationStorage,
    private val reportCalculationEventPublisher: RabbitMqReportCalculationEventPublisher
) : CreateReportCalculationUseCase {

    override fun createReportCalculation(request: CreateReportCalculationRequest): CreateReportCalculationResult {
        val calculation = ReportCalculation(
            id = "ReportCalculation:${UUID.randomUUID()}",
            report = request.report,
            status = ReportCalculationStatus.PENDING,
            args = request.args
        )

        return try {
            val reportCalculations = reportCalculationStorage.fetchReportCalculations(request.report)

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
                handleResponse(reportCalculationStorage.storeCalculation(calculation))
            }
        } catch (ex: Exception) {
            handleError(ex)
        }
    }

    private fun handleResponse(reportCalculation: ReportCalculation): CreateReportCalculationResult {
        reportCalculationEventPublisher.publish(
            "report.calculation",
            ReportCalculationEvent(
//                tenant = "<customer>", // prepare tenant support
                reportCalculationId = reportCalculation.id,
            )
        )
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