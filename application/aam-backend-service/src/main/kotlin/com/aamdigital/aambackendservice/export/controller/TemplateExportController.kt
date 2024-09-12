package com.aamdigital.aambackendservice.export.controller

import com.aamdigital.aambackendservice.domain.DomainReference
import com.aamdigital.aambackendservice.domain.UseCaseOutcome.Failure
import com.aamdigital.aambackendservice.domain.UseCaseOutcome.Success
import com.aamdigital.aambackendservice.error.ExternalSystemException
import com.aamdigital.aambackendservice.error.InternalServerException
import com.aamdigital.aambackendservice.error.InvalidArgumentException
import com.aamdigital.aambackendservice.export.core.CreateTemplateErrorCode
import com.aamdigital.aambackendservice.export.core.CreateTemplateRequest
import com.aamdigital.aambackendservice.export.core.CreateTemplateUseCase
import com.aamdigital.aambackendservice.export.core.RenderTemplateErrorCode
import com.aamdigital.aambackendservice.export.core.RenderTemplateRequest
import com.aamdigital.aambackendservice.export.core.RenderTemplateUseCase
import com.fasterxml.jackson.databind.JsonNode
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.codec.multipart.FilePart
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono


/**
 * @param templateId The external identifier of the implementing TemplateEngine
 */
data class CreateTemplateResponseDto(
    val templateId: String,
)

/**
 * REST controller responsible for handling export operations related to templates.
 * Provides endpoints for checking status, posting a new template, and rendering a template.
 *
 * In Aam, this API is especially used for generating PDFs for an entity.
 *
 * @param webClient The WebClient used to interact with external services.
 * @param createTemplateUseCase Use case for creating a new template.
 * @param renderTemplateUseCase Use case for rendering an existing template.
 */
@RestController
@RequestMapping("/v1/export")
@Validated
class TemplateExportController(
    @Qualifier("aam-render-api-client") val webClient: WebClient,
    val createTemplateUseCase: CreateTemplateUseCase,
    val renderTemplateUseCase: RenderTemplateUseCase,
) {

    @GetMapping("/status")
    fun getStatus(): Mono<String> {
        return webClient.get().uri("/status").exchangeToMono {
            it.bodyToMono()
        }
    }

    @PostMapping("/template")
    fun postTemplate(
        @RequestPart("template") file: FilePart
    ): Mono<CreateTemplateResponseDto> {
        return createTemplateUseCase
            .execute(
                CreateTemplateRequest(
                    file = file
                )
            ).handle { result, sink ->
                when (result) {
                    is Success ->
                        sink.next(
                            CreateTemplateResponseDto(
                                templateId = result.outcome.templateRef.id
                            )
                        )

                    is Failure -> sink.error(
                        result.cause ?: getError(result.errorCode)
                    )
                }
            }
    }


    @PostMapping("/render/{templateId}")
    fun getTemplate(
        @PathVariable templateId: String,
        @RequestBody templateData: JsonNode,
    ): Mono<ResponseEntity<DataBuffer>> {
        return renderTemplateUseCase.execute(
            RenderTemplateRequest(
                templateRef = DomainReference(templateId),
                bodyData = templateData
            )
        ).handle { result, sink ->
            when (result) {
                is Success -> {
                    val headers = HttpHeaders()
                    headers.add(HttpHeaders.CONTENT_TYPE, getContentType(templateData))

                    sink.next(ResponseEntity(result.outcome.file, headers, HttpStatus.OK))
                }

                is Failure ->
                    sink.error(
                        getError(
                            result.errorCode,
                            "[${result.errorCode}] ${result.errorMessage}".trimIndent()
                        )
                    )
            }
        }
    }

    private fun getContentType(templateData: JsonNode): String = when (templateData.get("convertTo").asText()) {
        "pdf" -> MediaType.APPLICATION_PDF_VALUE
        "csv" -> "text/csv"
        "ods" -> "application/vnd.oasis.opendocument.spreadsheet"
        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        "xls" -> "application/vnd.ms-excel"
        "odp" -> "application/vnd.oasis.opendocument.presentation"
        "ppt" -> "application/vnd.ms-powerpoint"
        "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        "odt" -> "application/vnd.oasis.opendocument.text"
        "doc" -> "application/msword"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "txt" -> MediaType.TEXT_PLAIN_VALUE
        "jpg" -> MediaType.IMAGE_JPEG_VALUE
        "png" -> MediaType.IMAGE_PNG_VALUE
        "epub" -> "application/epub+zip"
        "html" -> MediaType.TEXT_HTML_VALUE
        "xml" -> MediaType.APPLICATION_XML_VALUE
        else -> throw InvalidArgumentException(
            message = "Invalid convertTo value",
            code = "INVALID_VALUE_FOR_CONVERT_TO"
        )
    }

    private fun getError(errorCode: RenderTemplateErrorCode, message: String): Throwable =
        when (errorCode) {
            RenderTemplateErrorCode.INTERNAL_SERVER_ERROR -> throw InternalServerException(message)
            RenderTemplateErrorCode.FETCH_TEMPLATE_FAILED_ERROR -> throw ExternalSystemException(message)
            RenderTemplateErrorCode.CREATE_RENDER_REQUEST_FAILED_ERROR -> throw ExternalSystemException(message)
            RenderTemplateErrorCode.FETCH_RENDER_ID_REQUEST_FAILED_ERROR -> throw ExternalSystemException(message)
            RenderTemplateErrorCode.PARSE_RESPONSE_ERROR -> throw ExternalSystemException(message)
        }

    private fun getError(errorCode: CreateTemplateErrorCode): Throwable =
        when (errorCode) {
            CreateTemplateErrorCode.INTERNAL_SERVER_ERROR -> throw InternalServerException()
            CreateTemplateErrorCode.PARSE_RESPONSE_ERROR -> throw ExternalSystemException()
            CreateTemplateErrorCode.CREATE_TEMPLATE_REQUEST_FAILED_ERROR -> throw ExternalSystemException()
        }
}
