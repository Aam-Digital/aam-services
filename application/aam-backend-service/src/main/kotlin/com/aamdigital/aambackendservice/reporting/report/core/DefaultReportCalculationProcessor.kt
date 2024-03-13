package com.aamdigital.aambackendservice.reporting.report.core

import com.aamdigital.aambackendservice.domain.ReportCalculation
import com.aamdigital.aambackendservice.domain.ReportCalculationOutcome
import com.aamdigital.aambackendservice.domain.ReportCalculationStatus
import com.aamdigital.aambackendservice.reporting.report.calculation.core.ReportCalculator
import reactor.core.publisher.Mono
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.*

class DefaultReportCalculationProcessor(
    private val reportingStorage: ReportingStorage,
    private val reportCalculator: ReportCalculator,
) : ReportCalculationProcessor {
    override fun processNextPendingCalculation(): Mono<Unit> {
        var calculation: ReportCalculation;
        return reportingStorage.fetchPendingCalculations()
            .flatMap { calculations ->
                calculation = calculations.firstOrNull()
                    ?: return@flatMap Mono.just(Unit)

                reportingStorage.storeCalculation(
                    reportCalculation = calculation
                        .setStatus(ReportCalculationStatus.RUNNING)
                        .setStartDate(
                            startDate = getIsoLocalDateTime()
                        )
                )
                    .flatMap {
                        reportCalculator.calculate(reportCalculation = it)
                    }
                    .flatMap { reportData ->
                        reportingStorage.storeData(
                            reportData
                        )
                    }
                    .flatMap { reportData ->
                        reportingStorage.storeCalculation(
                            reportCalculation = calculation
                                .setStatus(ReportCalculationStatus.FINISHED_SUCCESS)
                                .setOutcome(
                                    ReportCalculationOutcome.Success(
                                        resultHash = reportData.getDataHash()
                                    )
                                )
                                .setEndDate(getIsoLocalDateTime())
                        ).map {}
                    }
                    .onErrorResume {
                        reportingStorage.storeCalculation(
                            reportCalculation = calculation
                                .setStatus(ReportCalculationStatus.FINISHED_ERROR)
                                .setOutcome(
                                    ReportCalculationOutcome.Failure(
                                        errorCode = "CALCULATION_FAILED",
                                        errorMessage = it.localizedMessage,
                                    )
                                )
                                .setEndDate(getIsoLocalDateTime())
                        ).map {}
                    }
            }
    }

    private fun getIsoLocalDateTime(): String = Date().toInstant()
        .atOffset(ZoneOffset.UTC)
        .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
}
