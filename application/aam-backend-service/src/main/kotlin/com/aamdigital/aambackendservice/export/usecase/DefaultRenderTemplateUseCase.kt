package com.aamdigital.aambackendservice.export.usecase

import com.aamdigital.aambackendservice.domain.DomainReference
import com.aamdigital.aambackendservice.domain.UseCaseOutcome
import com.aamdigital.aambackendservice.domain.UseCaseOutcome.Success
import com.aamdigital.aambackendservice.error.ExternalSystemException
import com.aamdigital.aambackendservice.error.NetworkException
import com.aamdigital.aambackendservice.error.NotFoundException
import com.aamdigital.aambackendservice.export.core.RenderTemplateData
import com.aamdigital.aambackendservice.export.core.RenderTemplateError
import com.aamdigital.aambackendservice.export.core.RenderTemplateError.CREATE_RENDER_REQUEST_FAILED_ERROR
import com.aamdigital.aambackendservice.export.core.RenderTemplateRequest
import com.aamdigital.aambackendservice.export.core.RenderTemplateUseCase
import com.aamdigital.aambackendservice.export.core.TemplateExport
import com.aamdigital.aambackendservice.export.core.TemplateStorage
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import java.io.InputStream

data class RenderRequestResponseDto(
    val success: Boolean,
    val data: RenderRequestResponseDataDto,
)

data class RenderRequestErrorResponseDto(
    val success: Boolean,
    val error: String,
)

data class RenderRequestResponseDataDto(
    val renderId: String,
)

/**
 * Default implementation of the [RenderTemplateUseCase] interface.
 *
 * This use case is responsible for creating a template rendering request to a specified template endpoint
 * and fetch the rendered file afterward.
 *
 * The file metadata is forwarded to the client.
 *
 * @property renderClient The RestClient used to make HTTP requests.
 * @property objectMapper The ObjectMapper used for JSON processing.
 * @property templateStorage The TemplateStorage used to fetch templates.
 */
class DefaultRenderTemplateUseCase(
    private val renderClient: RestClient,
    private val objectMapper: ObjectMapper,
    private val templateStorage: TemplateStorage,
) : RenderTemplateUseCase() {

    private data class FileResponse(
        val file: InputStream,
        val headers: HttpHeaders,
    )

    override fun apply(
        request: RenderTemplateRequest
    ): UseCaseOutcome<RenderTemplateData> {

        val template = fetchTemplate(request.templateRef)

        val targetFileName = template.targetFileName
            .replace(Regex("[\\\\/*?\"<>|]"), "_")

        (request.bodyData as ObjectNode).put(
            "reportName",
            targetFileName.replace(Regex("[\\\\/*?\"<>|]"), "_")
        )

        val templateId = createRenderRequest(template.templateId, request.bodyData)
        val renderId = parseRenderRequestResponse(templateId)
        val fileResponse = fetchRenderIdRequest(renderId)

        return Success(
            data = RenderTemplateData(
                file = fileResponse.file,
                responseHeaders = fileResponse.headers
            )
        )
    }

    private fun fetchTemplate(templateRef: DomainReference): TemplateExport {
        return try {
            templateStorage.fetchTemplate(templateRef)
        } catch (ex: Exception) {
            throw when (ex) {
                is NotFoundException -> NotFoundException(
                    cause = ex.cause ?: ex,
                    message = ex.localizedMessage,
                    code = RenderTemplateError.NOT_FOUND_ERROR
                )

                is NetworkException -> NetworkException(
                    cause = ex.cause ?: ex,
                    message = ex.localizedMessage,
                    code = RenderTemplateError.FETCH_TEMPLATE_FAILED_ERROR
                )

                else -> ExternalSystemException(
                    cause = ex.cause ?: ex,
                    message = "Could not create render request to template engine.",
                    code = RenderTemplateError.FETCH_TEMPLATE_FAILED_ERROR
                )

            }
        }
    }

    private fun createRenderRequest(templateId: String, bodyData: JsonNode): String {
        val response = try {
            renderClient.post()
                .uri("/render/$templateId")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(bodyData)
                .retrieve()
                .body(String::class.java)
        } catch (ex: Exception) {
            throw ExternalSystemException(
                cause = ex,
                message = ex.localizedMessage,
                code = CREATE_RENDER_REQUEST_FAILED_ERROR
            )
        }

        if (response.isNullOrEmpty()) {
            throw ExternalSystemException(
                cause = null,
                message = "Null or empty response from renderClient.",
                code = CREATE_RENDER_REQUEST_FAILED_ERROR
            )
        }

        return response
    }

    private fun fetchRenderIdRequest(renderId: String): FileResponse {
        return try {
            renderClient.get()
                .uri("/render/$renderId")
                .accept(MediaType.APPLICATION_JSON)
                .exchange { _, clientResponse ->
                    val responseHeaders = clientResponse.headers

                    val forwardHeaders = HttpHeaders()
                    forwardHeaders.contentType = responseHeaders.contentType

                    if (!responseHeaders["Content-Disposition"].isNullOrEmpty()) {
                        forwardHeaders["Content-Disposition"] = responseHeaders["Content-Disposition"]
                    }

                    val buffer = clientResponse.bodyTo(ByteArray::class.java) ?: throw ExternalSystemException(
                        cause = null,
                        message = "Could not convert body to Resource.",
                        code = RenderTemplateError.FETCH_RENDER_ID_REQUEST_FAILED_ERROR
                    )

                    FileResponse(
                        file = buffer.inputStream(),
                        headers = forwardHeaders
                    )
                }
        } catch (ex: Exception) {
            throw when (ex) {
                is ResourceAccessException -> NetworkException(
                    cause = ex.cause ?: ex,
                    message = ex.localizedMessage,
                    code = RenderTemplateError.FETCH_RENDER_ID_REQUEST_FAILED_ERROR
                )

                else -> ExternalSystemException(
                    cause = ex.cause ?: ex,
                    message = "Could not create render request to template engine.",
                    code = RenderTemplateError.FETCH_RENDER_ID_REQUEST_FAILED_ERROR
                )
            }
        }
    }

    private fun parseRenderRequestResponse(raw: String): String {
        try {
            val renderApiClientResponse = objectMapper.readValue(raw, RenderRequestResponseDto::class.java)
            return renderApiClientResponse.data.renderId
        } catch (ex: Exception) {
            val renderApiClientResponse = try {
                val response = objectMapper.readValue(raw, RenderRequestErrorResponseDto::class.java)
                response.error
            } catch (ex: Exception) {
                ex.localizedMessage
            }

            throw ExternalSystemException(
                cause = ex,
                message = renderApiClientResponse,
                code = RenderTemplateError.PARSE_RESPONSE_ERROR
            )
        }
    }
}
