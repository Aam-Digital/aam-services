package com.aamdigital.aambackendservice.reporting.reportcalculation.core

import com.aamdigital.aambackendservice.couchdb.core.CouchDbClient
import com.aamdigital.aambackendservice.error.AamErrorCode
import com.aamdigital.aambackendservice.error.InvalidArgumentException
import com.aamdigital.aambackendservice.error.NotFoundException
import com.aamdigital.aambackendservice.reporting.domain.ReportCalculation
import com.aamdigital.aambackendservice.reporting.report.core.QueryStorage
import com.aamdigital.aambackendservice.reporting.report.sqs.QueryRequest
import com.aamdigital.aambackendservice.reporting.storage.DefaultReportStorage
import org.springframework.stereotype.Service

@Service
class DefaultReportCalculator(
    private val reportStorage: DefaultReportStorage,
    private val queryStorage: QueryStorage,
    private val couchDbClient: CouchDbClient,
) : ReportCalculator {

    enum class DefaultReportCalculatorErrorCode : AamErrorCode {
        NOT_FOUND,
        INVALID_ARGUMENT,
    }

    companion object {
        const val DEFAULT_FROM_DATE = "0000-01-01"
        const val DEFAULT_TO_DATE = "9999-12-31T23:59:59.999Z"
        const val INDEX_ISO_STRING_DATE_END = 10
    }

    override fun calculate(reportCalculation: ReportCalculation): ReportCalculation {
        val report = reportStorage.fetchReport(reportCalculation.report).orElseThrow {
            NotFoundException(
                message = "[DefaultReportCalculator] Could not fetch Report ${reportCalculation.report.id}",
                code = DefaultReportCalculatorErrorCode.NOT_FOUND
            )
        }

        if (report.mode != "sql") {
            throw InvalidArgumentException(
                message = "[DefaultReportCalculator] Just 'sql' reports are supported.",
                code = DefaultReportCalculatorErrorCode.INVALID_ARGUMENT
            )
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
        )

        return reportCalculation
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
                        message = "Argument $it is missing. " +
                                "All report args are needed for a successful ReportCalculation.",
                        code = DefaultReportCalculatorErrorCode.NOT_FOUND
                    )
            }
    }

    private fun getDefaultValue(arg: String): String? = when (arg) {
        "from" -> DEFAULT_FROM_DATE
        "to" -> DEFAULT_TO_DATE
        else -> null
    }
}
