package com.aamdigital.aambackendservice.reporting.report.sqs

import com.aamdigital.aambackendservice.common.error.AamErrorCode
import com.aamdigital.aambackendservice.common.error.ExternalSystemException
import com.aamdigital.aambackendservice.common.error.InvalidArgumentException
import com.aamdigital.aambackendservice.reporting.report.core.QueryStorage
import org.slf4j.LoggerFactory
import org.springframework.core.io.Resource
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.client.ClientHttpResponse
import org.springframework.web.client.RestClient
import java.io.InputStream

data class QueryRequest(
    val query: String,
    val args: List<String>
)

/**
 * Runs report queries on SQS.
 *
 * The body of an SQS error response can quote the tenant's query, so it is kept only where the
 * report author needs it:
 * - A 400 means the query is invalid. The body is SQS's explanation of why and stays in the
 *   [InvalidArgumentException] message, which becomes the calculation's `errorDetails` that the API
 *   returns to the report editor. A calculation failing with this cause is only logged at INFO.
 * - Any other error status (e.g. wrong credentials or a failure of SQS itself) is not the report
 *   author's to fix and is logged at ERROR, where the exception message becomes the title of a
 *   Sentry event. That message leaves the body out, which is logged at DEBUG instead.
 */
class SqsQueryStorage(
    private val sqsClient: RestClient,
    private val schemaService: SqsSchemaService
) : QueryStorage {
    private val logger = LoggerFactory.getLogger(javaClass)

    enum class SqsQueryStorageErrorCode : AamErrorCode {
        EMPTY_RESPONSE,

        /** SQS rejected the query as invalid (400) - an invalid query in the ReportConfig. */
        QUERY_FAILED,

        /**
         * SQS answered with any other error status: another 4xx (e.g. wrong credentials or a missing
         * design document) or a 5xx.
         */
        QUERY_EXECUTION_FAILED
    }

    companion object {
        // SQS error bodies are small; cap defensively so we never log/forward an unbounded response
        private const val MAX_ERROR_BODY_LENGTH = 500
    }

    override fun executeQuery(
        query: QueryRequest,
        reportId: String
    ): InputStream {
        val schemaPath = schemaService.getSchemaPath()
        schemaService.updateSchema()

        val response =
            sqsClient
                .post()
                .uri(schemaPath)
                .contentType(MediaType.APPLICATION_JSON)
                .body(query)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                // translate error statuses into typed AamExceptions instead of an untyped
                // HttpClientErrorException. Only a 400 means the query itself is invalid, which is the
                // report author's to fix; any other error status is a problem of SQS or its deployment
                // and must alert. The first matching handler applies, so the 400 one has to come first.
                .onStatus({ it.isSameCodeAs(HttpStatus.BAD_REQUEST) }) { _, clientResponse ->
                    throw InvalidArgumentException(
                        message =
                            "[SqsQueryStorage] SQS rejected the query for report '$reportId' " +
                                "(${clientResponse.statusCode}): ${readErrorBody(clientResponse)}",
                        code = SqsQueryStorageErrorCode.QUERY_FAILED
                    )
                }.onStatus({ it.isError }) { _, clientResponse ->
                    if (logger.isDebugEnabled) {
                        logger.debug(
                            "[SqsQueryStorage] SQS response to the query for report {} ({}): {}",
                            reportId,
                            clientResponse.statusCode,
                            readErrorBody(clientResponse)
                        )
                    }
                    throw ExternalSystemException(
                        message =
                            "[SqsQueryStorage] SQS failed to execute the query for report '$reportId' " +
                                "(${clientResponse.statusCode})",
                        code = SqsQueryStorageErrorCode.QUERY_EXECUTION_FAILED
                    )
                }.body(Resource::class.java)

        if (response == null) {
            throw ExternalSystemException(
                message = "[SqsQueryStorage] Could not fetch response from SQS for report '$reportId'",
                code = SqsQueryStorageErrorCode.EMPTY_RESPONSE
            )
        }

        return response.inputStream
    }

    private fun readErrorBody(response: ClientHttpResponse): String =
        runCatching {
            response.body
                .readBytes()
                .decodeToString()
                .trim()
                .take(MAX_ERROR_BODY_LENGTH)
        }.getOrDefault("<error body unavailable>")
}
