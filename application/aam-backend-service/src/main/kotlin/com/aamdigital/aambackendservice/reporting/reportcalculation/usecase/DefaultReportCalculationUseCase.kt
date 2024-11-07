package com.aamdigital.aambackendservice.reporting.reportcalculation.usecase

import com.aamdigital.aambackendservice.domain.DomainReference
import com.aamdigital.aambackendservice.domain.UseCaseOutcome
import com.aamdigital.aambackendservice.error.AamException
import com.aamdigital.aambackendservice.error.ExternalSystemException
import com.aamdigital.aambackendservice.error.InternalServerException
import com.aamdigital.aambackendservice.error.InvalidArgumentException
import com.aamdigital.aambackendservice.error.NetworkException
import com.aamdigital.aambackendservice.error.NotFoundException
import com.aamdigital.aambackendservice.reporting.domain.DataTransformation
import com.aamdigital.aambackendservice.reporting.domain.Report
import com.aamdigital.aambackendservice.reporting.domain.ReportCalculation
import com.aamdigital.aambackendservice.reporting.domain.ReportCalculationStatus
import com.aamdigital.aambackendservice.reporting.domain.ReportItem
import com.aamdigital.aambackendservice.reporting.report.core.QueryStorage
import com.aamdigital.aambackendservice.reporting.report.core.ReportStorage
import com.aamdigital.aambackendservice.reporting.report.sqs.QueryRequest
import com.aamdigital.aambackendservice.reporting.reportcalculation.core.ReportCalculationData
import com.aamdigital.aambackendservice.reporting.reportcalculation.core.ReportCalculationError
import com.aamdigital.aambackendservice.reporting.reportcalculation.core.ReportCalculationRequest
import com.aamdigital.aambackendservice.reporting.reportcalculation.core.ReportCalculationStorage
import com.aamdigital.aambackendservice.reporting.reportcalculation.core.ReportCalculationUseCase
import org.springframework.stereotype.Component
import java.io.InputStream
import java.io.SequenceInputStream
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.*

/**
 * Run ReportCalculation queries against SQS and store aggregate result in CouchDb attachment
 *
 * Will replace placeholder variables with values stored in ReportCalculation.args
 *
 * special handling for placeholder 'from' and 'to':
 *  There are currently several versions of data in the database. In order to support all formats,
 *  the 'from' and 'to' args are adapted to sqs before the request
 *
 *  Currently, these 'date' formats are used in our entities:
 *  - '2022-04-25'
 *  - '2022-04-25T22:00:000Z' (deprecated since some years, but still existing for old data)
 *
 *  For sqs, we will cut the 'from' date always to '2022-04-25'
 *
 *  If no date args are set, the default values are passed, to fetch all matching entities from all time
 *
 */
@Component
class DefaultReportCalculationUseCase(
    private val reportCalculationStorage: ReportCalculationStorage,
    private val reportStorage: ReportStorage,
    private val transformations: List<DataTransformation<String>>,
    private val queryStorage: QueryStorage,
) : ReportCalculationUseCase() {

    override fun apply(request: ReportCalculationRequest): UseCaseOutcome<ReportCalculationData> {

        val reportCalculation: ReportCalculation = try {
            reportCalculationStorage.fetchReportCalculation(
                DomainReference(request.reportCalculationId)
            )
        } catch (ex: AamException) {
            return handleException(ex, ReportCalculationError.REPORT_CALCULATION_NOT_FOUND)
        }

        val report: Report = try {
            reportStorage.fetchReport(reportCalculation.report)
        } catch (ex: AamException) {
            return handleException(ex, ReportCalculationError.REPORT_NOT_FOUND)
        }

        try {
            reportCalculationStorage.storeCalculation(
                reportCalculation = reportCalculation.setStatus(ReportCalculationStatus.RUNNING).setStartDate(
                    startDate = getIsoLocalDateTime()
                )
            )

            handleSqlReport(report, reportCalculation)
            markCalculationAsSuccess(request)
        } catch (ex: Exception) {
            reportCalculationStorage.storeCalculation(
                reportCalculation = reportCalculation.setStatus(ReportCalculationStatus.FINISHED_ERROR).setEndDate(
                    endDate = getIsoLocalDateTime()
                ).setErrorDetails(ex.localizedMessage)
            )

            return handleException(ex)
        }

        return UseCaseOutcome.Success(
            data = ReportCalculationData(
                reportCalculation = reportCalculation,
            )
        )
    }

    @Throws(AamException::class)
    private fun handleSqlReport(
        report: Report, reportCalculation: ReportCalculation
    ): UseCaseOutcome<ReportCalculationData> {

        for ((argKey: String, transformationKeys: List<String>) in report.transformations) {
            handleTransformations(argKey, transformationKeys, reportCalculation.args)
        }

        val resultData = if (report.version == 1) {
            listOf(
                handleReportItems(
                    queries = report.items, report = report, reportCalculation = reportCalculation
                ),
            )
        } else {
            listOf(
                "[".byteInputStream(),
                handleReportItems(
                    queries = report.items, report = report, reportCalculation = reportCalculation
                ),
                "]".byteInputStream()
            )
        }

        val result = reportCalculationStorage.addReportCalculationData(
            reportCalculation = reportCalculation, file = SequenceInputStream(
                Collections.enumeration(resultData)
            )
        )

        return UseCaseOutcome.Success(
            data = ReportCalculationData(
                reportCalculation = result
            )
        )

    }

    private fun handleReportItems(
        queries: List<ReportItem>,
        report: Report,
        reportCalculation: ReportCalculation
    ): InputStream {
        val queryStreams = queries.mapIndexed { index, queryItem ->
            val result: MutableList<InputStream> = when (queryItem) {
                is ReportItem.ReportQuery -> {
                    val queryResult = queryStorage.executeQuery(
                        handleReportQuery(queryItem, report, reportCalculation)
                    )
                    mutableListOf(queryResult)
                }

                is ReportItem.ReportGroup -> {
                    val prefix = "{\"${queryItem.title}\":[".byteInputStream()
                    val queryResult = handleReportItems(queryItem.items, report, reportCalculation)
                    val suffix = "]}".byteInputStream()
                    mutableListOf(prefix, queryResult, suffix)
                }
            }

            if (index < queries.size - 1) {
                result.add(",".byteInputStream())
            }

            SequenceInputStream(
                Collections.enumeration(
                    result
                )
            )
        }

        return SequenceInputStream(
            Collections.enumeration(
                queryStreams
            )
        )
    }

    private fun handleReportQuery(
        query: ReportItem.ReportQuery,
        report: Report,
        reportCalculation: ReportCalculation
    ): QueryRequest {
        return when (report.version) {
            1 -> {
                QueryRequest(
                    query = query.sql,
                    args = reportCalculation.args.values.toList()
                )
            }

            2 -> {
                getQueryRequest(query, reportCalculation.args)
            }

            else ->
                throw InvalidArgumentException(
                    message = "Reports with version ${report.version} are not supported yet",
                    code = ReportCalculationError.UNSUPPORTED_REPORT_VERSION
                )
        }
    }

    private fun markCalculationAsSuccess(request: ReportCalculationRequest) {
        reportCalculationStorage.storeCalculation(
            reportCalculation = reportCalculationStorage
                .fetchReportCalculation(
                    DomainReference(request.reportCalculationId)
                )
                .setStatus(ReportCalculationStatus.FINISHED_SUCCESS)
                .setEndDate(
                    endDate = getIsoLocalDateTime()
                )
        )
    }

    private fun getQueryRequest(query: ReportItem.ReportQuery, args: MutableMap<String, String>): QueryRequest {
        var sqlQuery = query.sql

        val findPlaceholderRegex = "\\$(\\w*)".toRegex()
        val placeholders = findPlaceholderRegex.findAll(sqlQuery)
            .map {
                it.groupValues[1]
            }

        val queryArgs = mutableListOf<String>()

        for (placeholder in placeholders) {
            val placeholderValue = args.getValue(placeholder)
            queryArgs.add(placeholderValue)
            sqlQuery = sqlQuery.replace("$$placeholder", "?")
        }

        return QueryRequest(
            query = sqlQuery,
            args = queryArgs
        )
    }

    private fun handleTransformations(
        argKey: String,
        transformationKeys: List<String>,
        args: MutableMap<String, String>
    ) {
        var value = args[argKey] ?: ""

        val transformationsToApply = transformationKeys.map { transformationKey ->
            transformations.find {
                it.id == transformationKey
            }
        }.mapNotNull { it }

        for (transformation in transformationsToApply) {
            value = transformation.transform(value)
        }
        args[argKey] = value
    }

    private fun handleException(
        exception: Exception,
        useCaseStepCode: ReportCalculationError? = null,
    ): UseCaseOutcome<ReportCalculationData> {
        val useCaseException: AamException = when (exception) {
            is NetworkException -> NetworkException(
                message = exception.localizedMessage,
                code = useCaseStepCode ?: ReportCalculationError.IO_ERROR,
                cause = exception
            )

            is NotFoundException -> NotFoundException(
                message = exception.localizedMessage,
                code = useCaseStepCode ?: ReportCalculationError.UNEXPECTED_ERROR,
                cause = exception
            )

            is ExternalSystemException -> ExternalSystemException(
                message = exception.localizedMessage,
                code = useCaseStepCode ?: ReportCalculationError.UNEXPECTED_ERROR,
                cause = exception
            )

            is InvalidArgumentException -> InvalidArgumentException(
                message = exception.localizedMessage,
                code = exception.code,
                cause = exception
            )

            else -> InternalServerException(
                message = exception.localizedMessage,
                code = ReportCalculationError.UNEXPECTED_ERROR,
                cause = exception.cause
            )
        }

        return UseCaseOutcome.Failure(
            errorMessage = useCaseException.localizedMessage,
            errorCode = useCaseException.code,
            cause = useCaseException.cause
        )
    }

    private fun getIsoLocalDateTime(): String = Date().toInstant()
        .atOffset(ZoneOffset.UTC)
        .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
}

