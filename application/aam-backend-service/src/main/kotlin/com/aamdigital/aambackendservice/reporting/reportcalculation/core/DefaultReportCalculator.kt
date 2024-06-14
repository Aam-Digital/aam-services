package com.aamdigital.aambackendservice.reporting.reportcalculation.core

import com.aamdigital.aambackendservice.domain.DomainReference
import com.aamdigital.aambackendservice.error.InvalidArgumentException
import com.aamdigital.aambackendservice.error.NotFoundException
import com.aamdigital.aambackendservice.reporting.domain.ReportCalculation
import com.aamdigital.aambackendservice.reporting.domain.ReportData
import com.aamdigital.aambackendservice.reporting.report.core.QueryStorage
import com.aamdigital.aambackendservice.reporting.report.core.ReportingStorage
import com.aamdigital.aambackendservice.reporting.report.sqs.QueryRequest
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.util.*
import kotlin.jvm.optionals.getOrDefault

@Service
class DefaultReportCalculator(
    private val reportingStorage: ReportingStorage,
    private val queryStorage: QueryStorage,
) : ReportCalculator {

    companion object {
        const val DEFAULT_FROM_DATE = "0000-01-01T00:00:00.000Z"
        const val DEFAULT_TO_DATE = "9999-12-31T23:59:59.999Z"
    }

    override fun calculate(reportCalculation: ReportCalculation): Mono<ReportData> {
        return reportingStorage.fetchReport(reportCalculation.report)
            .flatMap { reportOptional ->
                val report = reportOptional.getOrDefault(null)
                    ?: return@flatMap Mono.error(NotFoundException())

                if (report.mode != "sql") {
                    return@flatMap Mono.error(InvalidArgumentException())
                }

                if (reportCalculation.args["from"] == reportCalculation.args["to"]) {
                    reportCalculation.args.remove("to")
                }

                queryStorage.executeQuery(
                    query = QueryRequest(
                        query = report.query,
                        args = getReportCalculationArgs(report.neededArgs, reportCalculation.args)
                    )
                )
                    .map { queryResult ->
                        ReportData(
                            id = "ReportData:${UUID.randomUUID()}",
                            report = reportCalculation.report,
                            calculation = DomainReference(reportCalculation.id),
                            data = queryResult.result
                        )
                    }
            }
    }

    private fun getReportCalculationArgs(neededArgs: List<String>, givenArgs: Map<String, String>): List<String> =
        neededArgs
            .map {
                givenArgs[it]
                    ?: getDefaultValue(it)
                    ?: throw NotFoundException(
                        "Argument $it is missing. All report args are needed for a successful ReportCalculation."
                    )
            }

    private fun getDefaultValue(arg: String): String? = when (arg) {
        "from" -> DEFAULT_FROM_DATE
        "to" -> DEFAULT_TO_DATE
        else -> null
    }
}
