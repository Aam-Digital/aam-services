package com.aamdigital.aambackendservice.reporting.report.core

import com.aamdigital.aambackendservice.domain.DomainReference
import com.aamdigital.aambackendservice.error.NotFoundException
import com.aamdigital.aambackendservice.reporting.domain.ReportCalculation
import com.aamdigital.aambackendservice.reporting.domain.ReportCalculationStatus
import com.aamdigital.aambackendservice.reporting.reportcalculation.core.ReportCalculator
import reactor.core.publisher.Mono
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.*

class DefaultReportCalculationProcessor(
    private val reportingStorage: ReportingStorage,
    private val reportCalculator: ReportCalculator,
) : ReportCalculationProcessor {
    override fun processNextPendingCalculation(): Mono<Unit> {
        var calculation: ReportCalculation
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
                    .flatMap {
                        reportingStorage.fetchCalculation(DomainReference(calculation.id))
                    }
                    .flatMap { updatedCalculation ->
                        reportingStorage.storeCalculation(
                            reportCalculation = updatedCalculation.orElseThrow {
                                NotFoundException(
                                    "[DefaultReportCalculationProcessor]" +
                                            " updated Calculation not available after reportCalculator.calculate()"
                                )
                            }
                                .setStatus(ReportCalculationStatus.FINISHED_SUCCESS)
                                .setEndDate(getIsoLocalDateTime())
                        ).map {}
                    }
                    .onErrorResume { error ->
                        /*
                            We should think about moving the "prefetch" inside the ReportCalculationStorage,
                            instead of manually think about this every time. The "prefetch" ensures,
                            that the latest calculation is edited
                        */
                        reportingStorage.fetchCalculation(DomainReference(calculation.id)).flatMap {
                            reportingStorage.storeCalculation(
                                reportCalculation = calculation
                                    .setStatus(ReportCalculationStatus.FINISHED_ERROR)
                                    .setErrorDetails(error.localizedMessage)
                                    .setEndDate(getIsoLocalDateTime())
                            ).map {}
                        }
                    }
            }
    }

    private fun getIsoLocalDateTime(): String = Date().toInstant()
        .atOffset(ZoneOffset.UTC)
        .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
}
