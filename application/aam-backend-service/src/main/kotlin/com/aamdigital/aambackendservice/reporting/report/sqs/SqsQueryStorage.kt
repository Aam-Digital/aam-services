package com.aamdigital.aambackendservice.reporting.report.sqs

import com.aamdigital.aambackendservice.common.error.AamErrorCode
import com.aamdigital.aambackendservice.common.error.ExternalSystemException
import com.aamdigital.aambackendservice.common.error.InvalidArgumentException
import com.aamdigital.aambackendservice.reporting.report.core.QueryStorage
import org.springframework.core.io.Resource
import org.springframework.http.MediaType
import org.springframework.http.client.ClientHttpResponse
import org.springframework.web.client.RestClient
import java.io.InputStream

data class QueryRequest(
    val query: String,
    val args: List<String>
)

class SqsQueryStorage(
    private val sqsClient: RestClient,
    private val schemaService: SqsSchemaService
) : QueryStorage {
    enum class SqsQueryStorageErrorCode : AamErrorCode {
        EMPTY_RESPONSE,

        /** SQS rejected the query (4xx) - typically an invalid query in the ReportConfig. */
        QUERY_FAILED,

        /** SQS failed to execute the query (5xx). */
        QUERY_EXECUTION_FAILED,
    }

    companion object {
        // SQS error bodies are small; cap defensively so we never log/forward an unbounded response
        private const val MAX_ERROR_BODY_LENGTH = 500
    }

    override fun executeQuery(query: QueryRequest): InputStream {
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
                // translate transport-level errors into typed AamExceptions that carry the SQS response
                // body, so an invalid ReportConfig query surfaces with an actionable message instead of an
                // opaque, untyped HttpClientErrorException
                .onStatus({ it.is4xxClientError }) { _, clientResponse ->
                    throw InvalidArgumentException(
                        message = "[SqsQueryStorage] SQS rejected the query " +
                            "(${clientResponse.statusCode}): ${readErrorBody(clientResponse)}",
                        code = SqsQueryStorageErrorCode.QUERY_FAILED,
                    )
                }.onStatus({ it.is5xxServerError }) { _, clientResponse ->
                    throw ExternalSystemException(
                        message = "[SqsQueryStorage] SQS failed to execute the query " +
                            "(${clientResponse.statusCode}): ${readErrorBody(clientResponse)}",
                        code = SqsQueryStorageErrorCode.QUERY_EXECUTION_FAILED,
                    )
                }.body(Resource::class.java)

        if (response == null) {
            throw ExternalSystemException(
                message = "[SqsQueryStorage] Could not fetch response from SQS",
                code = SqsQueryStorageErrorCode.EMPTY_RESPONSE
            )
        }

        return response.inputStream
    }

    private fun readErrorBody(response: ClientHttpResponse): String =
        runCatching {
            response.body.readBytes().decodeToString().trim().take(MAX_ERROR_BODY_LENGTH)
        }.getOrDefault("<error body unavailable>")
}
