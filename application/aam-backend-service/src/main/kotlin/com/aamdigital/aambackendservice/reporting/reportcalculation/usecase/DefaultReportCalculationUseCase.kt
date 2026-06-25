package com.aamdigital.aambackendservice.reporting.reportcalculation.usecase

import com.aamdigital.aambackendservice.common.domain.DomainReference
import com.aamdigital.aambackendservice.common.domain.UseCaseOutcome
import com.aamdigital.aambackendservice.common.error.AamException
import com.aamdigital.aambackendservice.common.error.ExternalSystemException
import com.aamdigital.aambackendservice.common.error.InternalServerException
import com.aamdigital.aambackendservice.common.error.InvalidArgumentException
import com.aamdigital.aambackendservice.common.error.NotFoundException
import com.aamdigital.aambackendservice.reporting.report.Report
import com.aamdigital.aambackendservice.reporting.report.ReportItem
import com.aamdigital.aambackendservice.reporting.report.core.QueryStorage
import com.aamdigital.aambackendservice.reporting.report.core.ReportStorage
import com.aamdigital.aambackendservice.reporting.report.sqs.QueryRequest
import com.aamdigital.aambackendservice.reporting.reportcalculation.ReportCalculation
import com.aamdigital.aambackendservice.reporting.reportcalculation.ReportCalculationStatus
import com.aamdigital.aambackendservice.reporting.reportcalculation.core.ReportCalculationData
import com.aamdigital.aambackendservice.reporting.reportcalculation.core.ReportCalculationError
import com.aamdigital.aambackendservice.reporting.reportcalculation.core.ReportCalculationRequest
import com.aamdigital.aambackendservice.reporting.reportcalculation.core.ReportCalculationStorage
import com.aamdigital.aambackendservice.reporting.reportcalculation.core.ReportCalculationUseCase
import com.aamdigital.aambackendservice.reporting.transformation.DataTransformation
import java.io.InputStream
import java.io.SequenceInputStream
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.*

/**
 * Run ReportCalculation queries against SQS and store aggregate result in CouchDb attachment.
 *
 * Replaces placeholder variables ($name) in SQL with values from ReportCalculation.args.
 * Legacy "from"/"to" arg keys are normalized to "startDate"/"endDate" before processing so that
 * calculations created before the v1→canonical migration continue to produce correct results.
 */
class DefaultReportCalculationUseCase(
    private val reportCalculationStorage: ReportCalculationStorage,
    private val reportStorage: ReportStorage,
    private val transformations: List<DataTransformation<String>>,
    private val queryStorage: QueryStorage
) : ReportCalculationUseCase() {
    override fun apply(request: ReportCalculationRequest): UseCaseOutcome<ReportCalculationData> {
        logger.trace("start processing report-calculation-request {}", request.reportCalculationId)

        val reportCalculation: ReportCalculation =
            try {
                reportCalculationStorage.fetchReportCalculation(
                    DomainReference(request.reportCalculationId)
                )
            } catch (ex: AamException) {
                return handleException(ex, ReportCalculationError.REPORT_CALCULATION_NOT_FOUND)
            }

        val report: Report =
            try {
                reportStorage.fetchReport(reportCalculation.report)
            } catch (ex: AamException) {
                return handleException(ex, ReportCalculationError.REPORT_NOT_FOUND)
            }

        var updatedReportCalculation: ReportCalculation
        try {
            updatedReportCalculation =
                reportCalculationStorage.storeCalculation(
                    reportCalculation =
                        reportCalculation
                            .setStatus(ReportCalculationStatus.RUNNING)
                            .setStartDate(startDate = getIsoLocalDateTime())
                )

            updatedReportCalculation = handleSqlReport(report, updatedReportCalculation)

            updatedReportCalculation =
                reportCalculationStorage.storeCalculation(
                    reportCalculation =
                        updatedReportCalculation
                            .setStatus(ReportCalculationStatus.FINISHED_SUCCESS)
                            .setEndDate(endDate = getIsoLocalDateTime())
                )
        } catch (ex: Exception) {
            reportCalculationStorage.storeCalculation(
                reportCalculation =
                    reportCalculation
                        .setStatus(ReportCalculationStatus.FINISHED_ERROR)
                        .setEndDate(
                            endDate = getIsoLocalDateTime()
                        ).setErrorDetails(ex.localizedMessage)
            )

            return handleException(ex)
        }

        return UseCaseOutcome.Success(
            data =
                ReportCalculationData(
                    reportCalculation = updatedReportCalculation
                )
        )
    }

    @Throws(AamException::class)
    private fun handleSqlReport(
        report: Report,
        reportCalculation: ReportCalculation
    ): ReportCalculation {
        normalizeLegacyArgs(reportCalculation.args)
        for ((argKey: String, transformationKeys: List<String>) in report.transformations) {
            handleTransformations(argKey, transformationKeys, reportCalculation.args)
        }

        // A single top-level query returns its rows directly as a flat JSON array ([{…},{…}]),
        // matching the documented ReportData schema that external consumers (e.g. TolaData) rely on.
        // Only reports with multiple items / groups get an enclosing array so each item's result
        // stays addressable as data[i]; wrapping a single query in that extra array would collapse
        // the result to a single row of nested objects for those flat consumers.
        val singleBareQuery =
            report.items.size == 1 && report.items.first() is ReportItem.ReportQuery

        val resultData =
            if (singleBareQuery) {
                listOf(
                    handleReportItems(
                        queries = report.items,
                        reportCalculation = reportCalculation
                    )
                )
            } else {
                listOf(
                    "[".byteInputStream(),
                    handleReportItems(
                        queries = report.items,
                        reportCalculation = reportCalculation
                    ),
                    "]".byteInputStream()
                )
            }

        val result =
            reportCalculationStorage.addReportCalculationData(
                reportCalculation = reportCalculation,
                file =
                    SequenceInputStream(
                        Collections.enumeration(resultData)
                    )
            )

        return result
    }

    private fun handleReportItems(
        queries: List<ReportItem>,
        reportCalculation: ReportCalculation
    ): InputStream {
        val queryStreams =
            queries.mapIndexed { index, queryItem ->
                val result: MutableList<InputStream> =
                    when (queryItem) {
                        is ReportItem.ReportQuery -> {
                            val queryResult =
                                queryStorage.executeQuery(
                                    handleReportQuery(queryItem, reportCalculation)
                                )
                            mutableListOf(queryResult)
                        }

                        is ReportItem.ReportGroup -> {
                            val prefix = "{\"${queryItem.title}\":[".byteInputStream()
                            val queryResult = handleReportItems(queryItem.items, reportCalculation)
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
        reportCalculation: ReportCalculation
    ): QueryRequest = getQueryRequest(query, reportCalculation.args)

    private fun getQueryRequest(
        query: ReportItem.ReportQuery,
        args: MutableMap<String, String>
    ): QueryRequest {
        var sqlQuery = query.sql

        val findPlaceholderRegex = "\\$(\\w*)".toRegex()
        val placeholders =
            findPlaceholderRegex
                .findAll(sqlQuery)
                .map {
                    it.groupValues[1]
                }

        val queryArgs = mutableListOf<String>()

        for (placeholder in placeholders) {
            if (!args.containsKey(placeholder)) {
                // skip potential placeholders that are not in args, these may be sqlite-related tokens (e.g. for json_extract)
                continue
            }

            val placeholderValue = args.getValue(placeholder)
            queryArgs.add(placeholderValue)
            sqlQuery = sqlQuery.replace("$$placeholder", "?")
        }

        return QueryRequest(
            query = sqlQuery,
            args = queryArgs
        )
    }

    /**
     * Normalize legacy "from"/"to" arg keys to canonical "startDate"/"endDate".
     *
     * This is independent of the ReportConfig migration in DefaultReportStorage: that migration
     * rewrites the stored config (SQL placeholders + transformation keys) to canonical form, but
     * it cannot touch the per-calculation runtime args. Those args are supplied by the caller —
     * ndb-core still posts "from"/"to" — and are also already present on ReportCalculation
     * documents created before the migration. Without this step, after the SQL is normalized to
     * $startDate/$endDate the transformation lookup would miss the "from"/"to" values and silently
     * fall back to all-time date defaults. Remove only once all callers send "startDate"/"endDate".
     */
    private fun normalizeLegacyArgs(args: MutableMap<String, String>) {
        if (!args.containsKey("startDate")) {
            args.remove("from")?.let { args["startDate"] = it }
        }
        if (!args.containsKey("endDate")) {
            args.remove("to")?.let { args["endDate"] = it }
        }
    }

    private fun handleTransformations(
        argKey: String,
        transformationKeys: List<String>,
        args: MutableMap<String, String>
    ) {
        var value = args[argKey] ?: ""

        val transformationsToApply =
            transformationKeys
                .map { transformationKey ->
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
        useCaseStepCode: ReportCalculationError? = null
    ): UseCaseOutcome<ReportCalculationData> {
        val useCaseException: AamException =
            when (exception) {
                is NotFoundException ->
                    NotFoundException(
                        message = exception.localizedMessage,
                        code = useCaseStepCode ?: ReportCalculationError.UNEXPECTED_ERROR,
                        cause = exception
                    )

                is ExternalSystemException ->
                    ExternalSystemException(
                        message = exception.localizedMessage,
                        code = useCaseStepCode ?: ReportCalculationError.UNEXPECTED_ERROR,
                        cause = exception
                    )

                is InvalidArgumentException ->
                    InvalidArgumentException(
                        message = exception.localizedMessage,
                        code = exception.code,
                        cause = exception
                    )

                else ->
                    InternalServerException(
                        message = exception.localizedMessage,
                        code = ReportCalculationError.UNEXPECTED_ERROR,
                        // keep `exception` itself, not `exception.cause`: the original error (e.g. the
                        // HttpClientErrorException from an invalid query) is the root cause we need in Sentry
                        cause = exception
                    )
            }

        return UseCaseOutcome.Failure(
            errorMessage = useCaseException.localizedMessage,
            errorCode = useCaseException.code,
            // propagate the full wrapper chain (useCaseException -> original exception), not just its cause,
            // so downstream listeners and Sentry receive the complete error trail
            cause = useCaseException
        )
    }

    private fun getIsoLocalDateTime(): String =
        Date()
            .toInstant()
            .atOffset(ZoneOffset.UTC)
            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
}
