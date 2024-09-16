package com.aamdigital.aambackendservice.export.usecase

import com.aamdigital.aambackendservice.domain.DomainReference
import com.aamdigital.aambackendservice.domain.UseCaseOutcome
import com.aamdigital.aambackendservice.domain.UseCaseOutcome.Success
import com.aamdigital.aambackendservice.error.AamException
import com.aamdigital.aambackendservice.error.ExternalSystemException
import com.aamdigital.aambackendservice.error.InvalidArgumentException
import com.aamdigital.aambackendservice.error.NotFoundException
import com.aamdigital.aambackendservice.export.core.RenderTemplateData
import com.aamdigital.aambackendservice.export.core.RenderTemplateErrorCode
import com.aamdigital.aambackendservice.export.core.RenderTemplateErrorCode.FETCH_RENDER_ID_REQUEST_FAILED_ERROR
import com.aamdigital.aambackendservice.export.core.RenderTemplateRequest
import com.aamdigital.aambackendservice.export.core.RenderTemplateUseCase
import com.aamdigital.aambackendservice.export.core.TemplateExport
import com.aamdigital.aambackendservice.export.core.TemplateStorage
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.slf4j.LoggerFactory
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.switchIfEmpty

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
 * @property webClient The WebClient used to make HTTP requests.
 * @property objectMapper The ObjectMapper used for JSON processing.
 * @property templateStorage The TemplateStorage used to fetch templates.
 */
class DefaultRenderTemplateUseCase(
    private val webClient: WebClient,
    private val objectMapper: ObjectMapper,
    private val templateStorage: TemplateStorage,
) : RenderTemplateUseCase {

    private val logger = LoggerFactory.getLogger(javaClass)

    private data class FileResponse(
        val file: DataBuffer,
        val headers: HttpHeaders,
    )

    override fun apply(
        request: RenderTemplateRequest
    ): Mono<UseCaseOutcome<RenderTemplateData, RenderTemplateErrorCode>> {
        return try {
            return fetchTemplateRequest(request.templateRef)
                .flatMap { template: TemplateExport ->
                    val fileName = template.targetFileName

                    (request.bodyData as ObjectNode).put("reportName", fileName)

                    createRenderRequest(template.templateId, request.bodyData)
                        .map { templateId: String ->
                            parseRenderRequestResponse(templateId)
                        }
                }
                .flatMap { renderId: String ->
                    fetchRenderIdRequest(renderId)
                }
                .flatMap { fileResponse: FileResponse ->
                    Mono.just(
                        Success(
                            outcome = RenderTemplateData(
                                file = fileResponse.file,
                                responseHeaders = fileResponse.headers
                            )
                        )
                    )
                }
        } catch (it: Exception) {
            handleError(it)
        }
    }

    override fun handleError(
        it: Throwable
    ): Mono<UseCaseOutcome<RenderTemplateData, RenderTemplateErrorCode>> {
        val errorCode: RenderTemplateErrorCode = runCatching {
            RenderTemplateErrorCode.valueOf((it as AamException).code)
        }.getOrDefault(RenderTemplateErrorCode.INTERNAL_SERVER_ERROR)

        logger.error("[${errorCode}] ${it.localizedMessage}", it.cause)

        return Mono.just(
            UseCaseOutcome.Failure(
                errorMessage = it.localizedMessage,
                errorCode = errorCode,
                cause = it.cause
            )
        )
    }

    private fun fetchTemplateRequest(templateRef: DomainReference): Mono<TemplateExport> {
        return templateStorage.fetchTemplate(templateRef)
            .switchIfEmpty {
                Mono.error(
                    ExternalSystemException(
                        cause = null,
                        message = "fetchTemplate() returned empty Mono",
                        code = RenderTemplateErrorCode.FETCH_TEMPLATE_FAILED_ERROR.toString()
                    )
                )
            }
            .onErrorMap {
                if (it is InvalidArgumentException) {
                    NotFoundException(
                        cause = it,
                        message = it.localizedMessage,
                        code = RenderTemplateErrorCode.NOT_FOUND_ERROR.toString()
                    )
                } else {
                    ExternalSystemException(
                        cause = it,
                        message = it.localizedMessage,
                        code = RenderTemplateErrorCode.FETCH_TEMPLATE_FAILED_ERROR.toString()
                    )
                }
            }
    }

    private fun createRenderRequest(templateId: String, bodyData: JsonNode): Mono<String> {
        return webClient.post()
            .uri("/render/$templateId")
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .body(BodyInserters.fromValue(bodyData))
            .exchangeToMono {
                it.bodyToMono(String::class.java)
            }
            .onErrorMap {
                ExternalSystemException(
                    cause = it,
                    message = it.localizedMessage,
                    code = RenderTemplateErrorCode.CREATE_RENDER_REQUEST_FAILED_ERROR.toString()
                )
            }
    }

    private fun fetchRenderIdRequest(renderId: String): Mono<FileResponse> {
        return webClient.get()
            .uri("/render/$renderId")
            .accept(MediaType.APPLICATION_JSON)
            .exchangeToMono { exchange ->
                exchange.bodyToMono(DataBuffer::class.java).map { dataBuffer ->
                    val responseHeaders = exchange.headers().asHttpHeaders()

                    val forwardHeaders = HttpHeaders()
                    forwardHeaders.contentType = responseHeaders.contentType

                    if (!responseHeaders["Content-Disposition"].isNullOrEmpty()) {
                        forwardHeaders["Content-Disposition"] = responseHeaders["Content-Disposition"]
                    }

                    FileResponse(
                        file = dataBuffer,
                        headers = forwardHeaders
                    )
                }
            }
            .onErrorMap {
                ExternalSystemException(
                    cause = it,
                    message = it.localizedMessage,
                    code = FETCH_RENDER_ID_REQUEST_FAILED_ERROR.toString()
                )
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
                code = RenderTemplateErrorCode.PARSE_RESPONSE_ERROR.toString()
            )
        }
    }
}
