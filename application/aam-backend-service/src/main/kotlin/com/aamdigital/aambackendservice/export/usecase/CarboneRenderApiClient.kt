package com.aamdigital.aambackendservice.export.usecase

import com.aamdigital.aambackendservice.common.domain.DomainReference
import com.aamdigital.aambackendservice.common.error.AamErrorCode
import com.aamdigital.aambackendservice.common.error.ExternalSystemException
import com.aamdigital.aambackendservice.common.error.NetworkException
import com.aamdigital.aambackendservice.common.error.NotFoundException
import com.aamdigital.aambackendservice.common.rest.truncateForLog
import com.aamdigital.aambackendservice.export.core.TemplateExport
import com.aamdigital.aambackendservice.export.core.TemplateStorage
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class RenderRequestResponseDto(
    val success: Boolean,
    val data: RenderRequestResponseDataDto,
)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class RenderRequestErrorResponseDto(
    val success: Boolean,
    val error: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class RenderRequestResponseDataDto(
    val renderId: String,
)

/**
 * Handles HTTP communication with the Carbone template engine.
 *
 * Error codes are injected per-use-case so each caller maps Carbone errors to its own domain error enum.
 * [batchRejectedCode] is optional; when provided, HTTP 5xx responses with a structured Carbone error body
 * are thrown with that code rather than [createRenderRequestFailedCode].
 */
internal class CarboneRenderApiClient(
    private val renderClient: RestClient,
    private val objectMapper: ObjectMapper,
    private val templateStorage: TemplateStorage,
    private val notFoundCode: AamErrorCode,
    private val fetchTemplateFailedCode: AamErrorCode,
    private val createRenderRequestFailedCode: AamErrorCode,
    private val batchRejectedCode: AamErrorCode? = null,
    private val fetchRenderResultFailedCode: AamErrorCode,
    private val parseResponseCode: AamErrorCode,
) {
    class RenderResult(
        val file: ByteArray,
        val headers: HttpHeaders,
    )

    fun fetchTemplate(templateRef: DomainReference): TemplateExport =
        try {
            templateStorage.fetchTemplate(templateRef)
        } catch (ex: Exception) {
            throw when (ex) {
                is NotFoundException ->
                    NotFoundException(
                        cause = ex.cause ?: ex,
                        message = ex.localizedMessage,
                        code = notFoundCode,
                    )

                is NetworkException ->
                    NetworkException(
                        cause = ex.cause ?: ex,
                        message = ex.localizedMessage,
                        code = fetchTemplateFailedCode,
                    )

                else ->
                    ExternalSystemException(
                        cause = ex.cause ?: ex,
                        message = "Could not fetch template metadata.",
                        code = fetchTemplateFailedCode,
                    )
            }
        }

    fun createRenderRequest(
        templateId: String,
        bodyData: JsonNode,
    ): String {
        val response =
            try {
                renderClient
                    .post()
                    .uri("/render/$templateId")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(bodyData)
                    .retrieve()
                    .body(String::class.java)
            } catch (ex: Exception) {
                if (batchRejectedCode != null) {
                    val carboneMessage = extractCarboneErrorMessage(ex)
                    if (carboneMessage != null) {
                        throw ExternalSystemException(
                            cause = ex,
                            message = carboneMessage,
                            code = batchRejectedCode,
                        )
                    }
                }
                throw ExternalSystemException(
                    cause = ex,
                    message = ex.localizedMessage,
                    code = createRenderRequestFailedCode,
                )
            }

        if (response.isNullOrEmpty()) {
            throw ExternalSystemException(
                cause = null,
                message = "Null or empty response from renderClient.",
                code = createRenderRequestFailedCode,
            )
        }

        return response
    }

    fun fetchRenderResult(renderId: String): RenderResult =
        try {
            renderClient
                .get()
                .uri("/render/$renderId")
                .accept(MediaType.APPLICATION_JSON)
                .exchange { _, clientResponse ->
                    val responseHeaders = clientResponse.headers
                    val forwardHeaders = HttpHeaders()
                    forwardHeaders.contentType = responseHeaders.contentType

                    if (!responseHeaders[HttpHeaders.CONTENT_DISPOSITION].isNullOrEmpty()) {
                        forwardHeaders[HttpHeaders.CONTENT_DISPOSITION] =
                            responseHeaders[HttpHeaders.CONTENT_DISPOSITION]
                    }

                    val buffer =
                        clientResponse.bodyTo(ByteArray::class.java) ?: throw ExternalSystemException(
                            cause = null,
                            message = "Could not read body bytes from template engine response.",
                            code = fetchRenderResultFailedCode,
                        )

                    RenderResult(file = buffer, headers = forwardHeaders)
                } ?: throw ExternalSystemException(
                cause = null,
                message = "Could not fetch render response from template engine.",
                code = fetchRenderResultFailedCode,
            )
        } catch (ex: Exception) {
            throw when (ex) {
                is ResourceAccessException ->
                    NetworkException(
                        cause = ex.cause ?: ex,
                        message = ex.localizedMessage,
                        code = fetchRenderResultFailedCode,
                    )

                else ->
                    ExternalSystemException(
                        cause = ex.cause ?: ex,
                        message = "Could not fetch render result from template engine.",
                        code = fetchRenderResultFailedCode,
                    )
            }
        }

    fun parseRenderId(raw: String): String {
        try {
            return objectMapper.readValue(raw, RenderRequestResponseDto::class.java).data.renderId
        } catch (ex: Exception) {
            val errorMessage =
                try {
                    objectMapper.readValue(raw, RenderRequestErrorResponseDto::class.java).error
                } catch (ignored: Exception) {
                    // neither the success nor the error shape matched; keep the actual response
                    "${ex.localizedMessage} (response body: ${raw.truncateForLog()})"
                }
            throw ExternalSystemException(
                cause = ex,
                message = errorMessage,
                code = parseResponseCode,
            )
        }
    }

    /**
     * Pull the structured `error` message out of a Carbone HTTP error response if present.
     * Expected body shape: `{"success":false,"error":"...","code":"w101","data":{"renderId":""}}`.
     */
    private fun extractCarboneErrorMessage(ex: Throwable): String? {
        var current: Throwable? = ex
        var body: String? = null
        while (current != null) {
            if (current is org.springframework.web.client.RestClientResponseException) {
                body = current.responseBodyAsString
                break
            }
            current = current.cause
        }
        if (body.isNullOrBlank()) return null
        return try {
            val parsed = objectMapper.readValue(body, RenderRequestErrorResponseDto::class.java)
            parsed.error.takeIf { it.isNotBlank() }
        } catch (ignored: Exception) {
            null
        }
    }
}
