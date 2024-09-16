package com.aamdigital.aambackendservice.export.usecase

import com.aamdigital.aambackendservice.domain.DomainReference
import com.aamdigital.aambackendservice.domain.UseCaseOutcome
import com.aamdigital.aambackendservice.error.AamException
import com.aamdigital.aambackendservice.error.ExternalSystemException
import com.aamdigital.aambackendservice.export.core.CreateTemplateData
import com.aamdigital.aambackendservice.export.core.CreateTemplateErrorCode
import com.aamdigital.aambackendservice.export.core.CreateTemplateErrorCode.CREATE_TEMPLATE_REQUEST_FAILED_ERROR
import com.aamdigital.aambackendservice.export.core.CreateTemplateErrorCode.PARSE_RESPONSE_ERROR
import com.aamdigital.aambackendservice.export.core.CreateTemplateRequest
import com.aamdigital.aambackendservice.export.core.CreateTemplateUseCase
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

data class CreateTemplateResponseDto(
    val success: Boolean,
    val data: CreateTemplateResponseDataDto
)

data class CreateTemplateResponseDataDto(
    val templateId: String,
)


/**
 * Default implementation of the [CreateTemplateUseCase] interface.
 *
 * This use case is responsible for creating a template by making a POST request to a specified
 * template creation endpoint. The TemplateExport entity is then created in the frontend.
 *
 * @property webClient The WebClient used to make HTTP requests to the template engine.
 * @property objectMapper The ObjectMapper used to parse JSON responses.
 */
class DefaultCreateTemplateUseCase(
    private val webClient: WebClient,
    private val objectMapper: ObjectMapper,
) : CreateTemplateUseCase {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun apply(
        request: CreateTemplateRequest
    ): Mono<UseCaseOutcome<CreateTemplateData, CreateTemplateErrorCode>> {
        val builder = MultipartBodyBuilder()

        builder
            .part("template", request.file)
            .filename(request.file.filename())

        return webClient.post()
            .uri("/template")
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .accept(MediaType.APPLICATION_JSON)
            .body(BodyInserters.fromMultipartData(builder.build()))
            .exchangeToMono {
                it.bodyToMono(String::class.java)
            }
            .onErrorMap {
                ExternalSystemException(
                    cause = it,
                    message = it.localizedMessage,
                    code = CREATE_TEMPLATE_REQUEST_FAILED_ERROR.toString()
                )
            }
            .map {
                UseCaseOutcome.Success(
                    outcome = CreateTemplateData(
                        templateRef = DomainReference(parseResponse(it))
                    )
                )
            }
    }

    override fun handleError(it: Throwable): Mono<UseCaseOutcome<CreateTemplateData, CreateTemplateErrorCode>> {
        val errorCode: CreateTemplateErrorCode = runCatching {
            CreateTemplateErrorCode.valueOf((it as AamException).code)
        }.getOrDefault(CreateTemplateErrorCode.INTERNAL_SERVER_ERROR)

        logger.error("[${errorCode}] ${it.localizedMessage}", it.cause)

        return Mono.just(
            UseCaseOutcome.Failure(
                errorMessage = it.localizedMessage,
                errorCode = errorCode,
                cause = it.cause
            )
        )
    }

    private fun parseResponse(raw: String): String {
        try {
            val renderApiClientResponse = objectMapper.readValue(raw, CreateTemplateResponseDto::class.java)
            return renderApiClientResponse.data.templateId
        } catch (ex: Exception) {

            throw ExternalSystemException(
                cause = ex,
                message = ex.localizedMessage,
                code = PARSE_RESPONSE_ERROR.toString()
            )
        }
    }
}
