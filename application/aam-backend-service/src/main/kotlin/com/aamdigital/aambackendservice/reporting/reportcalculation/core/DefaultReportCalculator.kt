package com.aamdigital.aambackendservice.reporting.reportcalculation.core

import com.aamdigital.aambackendservice.couchdb.core.CouchDbClient
import com.aamdigital.aambackendservice.error.InvalidArgumentException
import com.aamdigital.aambackendservice.error.NotFoundException
import com.aamdigital.aambackendservice.reporting.domain.ReportCalculation
import com.aamdigital.aambackendservice.reporting.report.core.QueryStorage
import com.aamdigital.aambackendservice.reporting.report.sqs.QueryRequest
import com.aamdigital.aambackendservice.reporting.storage.DefaultReportStorage
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import kotlin.jvm.optionals.getOrDefault

@Service
class DefaultReportCalculator(
    private val reportStorage: DefaultReportStorage,
    private val queryStorage: QueryStorage,
    private val couchDbClient: CouchDbClient,
) : ReportCalculator {

    companion object {
        const val DEFAULT_FROM_DATE = "0000-01-01"
        const val DEFAULT_TO_DATE = "9999-12-31T23:59:59.999Z"
        const val INDEX_ISO_STRING_DATE_END = 10
    }

    override fun calculate(reportCalculation: ReportCalculation): Mono<ReportCalculation> {
        return reportStorage.fetchReport(reportCalculation.report)
            .flatMap { reportOptional ->
                val report = reportOptional.getOrDefault(null)
                    ?: return@flatMap Mono.error(NotFoundException())

                if (report.mode != "sql") {
                    return@flatMap Mono.error(InvalidArgumentException())
                }

                setToDateToLastMinuteOfDay(reportCalculation.args)

                val queryResult = queryStorage.executeQuery(
                    query = QueryRequest(
                        query = report.query,
                        args = getReportCalculationArgs(report.neededArgs, reportCalculation.args)
                    )
                )

                couchDbClient.putAttachment(
                    database = "report-calculation",
                    documentId = reportCalculation.id,
                    attachmentId = "data.json",
                    file = queryResult,
                ).map {
                    reportCalculation
                }
            }
    }

    private fun setToDateToLastMinuteOfDay(args: MutableMap<String, String>) {
        val toDateString = args["to"] ?: return
        args["to"] = toDateString.substring(0, INDEX_ISO_STRING_DATE_END) + "T23:59:59.999Z"
    }

    /**
     * There are currently several versions of data in the database. In order to support all formats,
     * the 'from' and 'to' args are adapted to sqs before the request
     *
     * Currently, these 'date' formats are used in our entities:
     * - '2022-04-25'
     * - '2022-04-25T22:00:000Z' (deprecated since some years, but still existing for old data)
     *
     * For sqs, we will cut the 'from' date always to '2022-04-25'
     *
     * If no date args are set, the default values are passed, to fetch all matching entities from all time
     *
     */
    private fun getReportCalculationArgs(
        neededArgs: List<String>,
        givenArgs: MutableMap<String, String>
    ): List<String> {
        givenArgs["from"]?.let {
            givenArgs["from"] = it.substring(0, INDEX_ISO_STRING_DATE_END)
        }

        return neededArgs
            .map {
                givenArgs[it]
                    ?: getDefaultValue(it)
                    ?: throw NotFoundException(
                        "Argument $it is missing. All report args are needed for a successful ReportCalculation."
                    )
            }
    }

    private fun getDefaultValue(arg: String): String? = when (arg) {
        "from" -> DEFAULT_FROM_DATE
        "to" -> DEFAULT_TO_DATE
        else -> null
    }
}
