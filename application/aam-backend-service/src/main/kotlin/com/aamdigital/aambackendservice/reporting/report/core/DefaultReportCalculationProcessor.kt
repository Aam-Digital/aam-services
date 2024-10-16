package com.aamdigital.aambackendservice.reporting.report.core

import com.aamdigital.aambackendservice.domain.DomainReference
import com.aamdigital.aambackendservice.error.AamErrorCode
import com.aamdigital.aambackendservice.error.InternalServerException
import com.aamdigital.aambackendservice.reporting.domain.ReportCalculationStatus
import com.aamdigital.aambackendservice.reporting.reportcalculation.core.ReportCalculator
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.*

class DefaultReportCalculationProcessor(
    private val reportingStorage: ReportingStorage,
    private val reportCalculator: ReportCalculator,
) : ReportCalculationProcessor {

    enum class DefaultReportCalculationProcessorErrorCode : AamErrorCode {
        CALCULATION_UPDATE_ERROR
    }

    override fun processNextPendingCalculation() {
        var calculation = reportingStorage.fetchPendingCalculations().firstOrNull()
            ?: return

        calculation = reportingStorage.storeCalculation(
            reportCalculation = calculation
                .setStatus(ReportCalculationStatus.RUNNING)
                .setStartDate(
                    startDate = getIsoLocalDateTime()
                )
        )

        try {
            reportCalculator.calculate(reportCalculation = calculation)

            calculation = reportingStorage.fetchCalculation(DomainReference(calculation.id)).orElseThrow {
                InternalServerException(
                    message = "[DefaultReportCalculationProcessor]" +
                            " updated Calculation not available after reportCalculator.calculate()",
                    code = DefaultReportCalculationProcessorErrorCode.CALCULATION_UPDATE_ERROR
                )
            }

            reportingStorage.storeCalculation(
                reportCalculation = calculation
                    .setStatus(ReportCalculationStatus.FINISHED_SUCCESS)
                    .setEndDate(getIsoLocalDateTime())
            )
        } catch (ex: Exception) {
            /*
                   We should think about moving the "prefetch" inside the ReportCalculationStorage,
                   instead of manually think about this every time. The "prefetch" ensures,
                   that the latest calculation is edited
               */
            reportingStorage.fetchCalculation(DomainReference(calculation.id)).ifPresent {
                reportingStorage.storeCalculation(
                    reportCalculation = it
                        .setStatus(ReportCalculationStatus.FINISHED_ERROR)
                        .setErrorDetails(ex.localizedMessage)
                        .setEndDate(getIsoLocalDateTime())
                )
            }
        }
    }

    private fun getIsoLocalDateTime(): String = Date().toInstant()
        .atOffset(ZoneOffset.UTC)
        .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
}
