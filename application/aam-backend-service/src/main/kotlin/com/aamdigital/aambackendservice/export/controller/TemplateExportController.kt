package com.aamdigital.aambackendservice.export.controller

import com.aamdigital.aambackendservice.common.domain.DomainReference
import com.aamdigital.aambackendservice.common.domain.UseCaseOutcome.Failure
import com.aamdigital.aambackendservice.common.domain.UseCaseOutcome.Success
import com.aamdigital.aambackendservice.common.error.HttpErrorDto
import com.aamdigital.aambackendservice.export.core.CreateTemplateError
import com.aamdigital.aambackendservice.export.core.CreateTemplateRequest
import com.aamdigital.aambackendservice.export.core.CreateTemplateUseCase
import com.aamdigital.aambackendservice.export.core.FetchTemplateError
import com.aamdigital.aambackendservice.export.core.FetchTemplateRequest
import com.aamdigital.aambackendservice.export.core.FetchTemplateUseCase
import com.aamdigital.aambackendservice.export.core.RenderTemplateError
import com.aamdigital.aambackendservice.export.core.RenderTemplateRequest
import com.aamdigital.aambackendservice.export.core.RenderTemplateUseCase
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody
import java.io.OutputStream


sealed interface TemplateExportControllerResponse {

    /**
     * @param templateId The external identifier of the implementing TemplateEngine
     */
    data class CreateTemplateControllerResponse(
        val templateId: String,
    ) : TemplateExportControllerResponse

    /**
     * StreamingResponse of the template binary file
     */
    fun interface FetchTemplateControllerResponse : StreamingResponseBody, TemplateExportControllerResponse

    /**
     * StreamingResponse of the template, rendered with passed data as binary file
     */
    fun interface RenderTemplateControllerResponse : StreamingResponseBody, TemplateExportControllerResponse

    class ErrorControllerResponse(
        errorCode: String,
        errorMessage: String
    ) : HttpErrorDto(
        errorCode,
        errorMessage
    ), TemplateExportControllerResponse
}

/**
 * REST controller responsible for handling export operations related to templates.
 * Provides endpoints for checking status, posting a new template, and rendering a template.
 *
 * In Aam, this API is especially used for generating PDFs for an entity.
 *
 * @param createTemplateUseCase Use case for creating a new template.
 * @param fetchTemplateUseCase Use case for fetching an existing template (file).
 * @param renderTemplateUseCase Use case for rendering an existing template.
 */
@RestController
@RequestMapping("/v1/export")
@ConditionalOnProperty(
    prefix = "features.export-api",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = false
)
@Validated
class TemplateExportController(
    private val createTemplateUseCase: CreateTemplateUseCase,
    private val fetchTemplateUseCase: FetchTemplateUseCase,
    private val renderTemplateUseCase: RenderTemplateUseCase,
    private val objectMapper: ObjectMapper,
) {

    companion object {
        private const val BYTE_ARRAY_BUFFER_LENGTH = 4096
    }

    private val logger = LoggerFactory.getLogger(javaClass)

    private fun getErrorEntity(
        errorCode: String,
        errorMessage: String,
        status: HttpStatus = HttpStatus.INTERNAL_SERVER_ERROR
    ): ResponseEntity<TemplateExportControllerResponse> = ResponseEntity
        .status(status)
        .body(
            TemplateExportControllerResponse.ErrorControllerResponse(
                errorMessage = errorMessage,
                errorCode = errorCode,
            )
        )

    /*
     * Needed so be able to return "ResponseEntity<StreamingResponseBody>" without the need to write a converter.
     */
    private fun getErrorStreamingBody(result: Failure<*>) =
        StreamingResponseBody { outputStream: OutputStream ->
            val buffer = ByteArray(BYTE_ARRAY_BUFFER_LENGTH)
            var bytesRead: Int

            val bodyStream = objectMapper.writeValueAsString(
                TemplateExportControllerResponse.ErrorControllerResponse(
                    errorCode = result.errorCode.toString(),
                    errorMessage = result.errorMessage,
                )
            ).byteInputStream()

            while ((bodyStream.read(buffer).also { bytesRead = it }) != -1) {
                outputStream.write(buffer, 0, bytesRead)
            }
        }

    @PostMapping("/template")
    fun postTemplate(
        @RequestPart("template") file: MultipartFile
    ): ResponseEntity<TemplateExportControllerResponse> {
        logger.trace("trying to create new Template");

        val result = createTemplateUseCase
            .run(
                CreateTemplateRequest(
                    file = file
                )
            )

        return when (result) {
            is Success -> {
                val response = TemplateExportControllerResponse.CreateTemplateControllerResponse(
                    templateId = result.data.templateRef.id
                )

                logger.trace(
                    "[TemplateExportController.postTemplate()] success response: {}",
                    response.toString()
                )

                ResponseEntity.ok(response)
            }

            is Failure -> {
                val responseEntity = when (result.errorCode as CreateTemplateError) {
                    else -> getErrorEntity(
                        errorCode = result.errorCode.toString(),
                        errorMessage = result.errorMessage
                    )
                }

                logger.trace(
                    "[TemplateExportController.postTemplate()] failure response: {}",
                    responseEntity.body.toString()
                )

                return responseEntity
            }
        }
    }

    @GetMapping("/template/{templateId}")
    fun fetchTemplate(
        @PathVariable templateId: String,
    ): ResponseEntity<StreamingResponseBody> {
        val result = fetchTemplateUseCase.run(
            FetchTemplateRequest(
                templateRef = DomainReference(templateId),
            )
        )

        return when (result) {
            is Success -> {
                val responseBody =
                    TemplateExportControllerResponse.FetchTemplateControllerResponse { outputStream: OutputStream ->
                        val buffer = ByteArray(BYTE_ARRAY_BUFFER_LENGTH)
                        var bytesRead: Int
                        while ((result.data.file.read(buffer).also { bytesRead = it }) != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                        }
                    }

                logger.trace(
                    "[TemplateExportController.fetchTemplate()] success response: (FetchTemplateControllerResponse)",
                )

                ResponseEntity(
                    responseBody,
                    result.data.responseHeaders,
                    HttpStatus.OK
                )
            }

            is Failure -> {
                val errorStreamingBody = getErrorStreamingBody(result)
                val headers = HttpHeaders()
                headers.set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)

                val responseEntity = when (result.errorCode as FetchTemplateError) {
                    FetchTemplateError.NOT_FOUND_ERROR -> ResponseEntity(
                        errorStreamingBody,
                        headers,
                        HttpStatus.NOT_FOUND,
                    )

                    else -> ResponseEntity(
                        errorStreamingBody,
                        headers,
                        HttpStatus.INTERNAL_SERVER_ERROR
                    )
                }

                return responseEntity
            }
        }
    }

    @PostMapping("/render/{templateId}")
    fun renderTemplate(
        @PathVariable templateId: String,
        @RequestBody templateData: JsonNode,
    ): ResponseEntity<StreamingResponseBody> {
        val result = renderTemplateUseCase.run(
            RenderTemplateRequest(
                templateRef = DomainReference(templateId),
                bodyData = templateData
            )
        )

        return when (result) {
            is Success -> {
                val responseBody =
                    TemplateExportControllerResponse.RenderTemplateControllerResponse { outputStream: OutputStream ->
                        val buffer = ByteArray(BYTE_ARRAY_BUFFER_LENGTH)
                        var bytesRead: Int
                        while ((result.data.file.read(buffer).also { bytesRead = it }) != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                        }
                    }

                logger.trace(
                    "[TemplateExportController.renderTemplate()] success response: (RenderTemplateControllerResponse)",
                )

                ResponseEntity(
                    responseBody,
                    result.data.responseHeaders,
                    HttpStatus.OK
                )
            }

            is Failure -> {
                val errorStreamingBody = getErrorStreamingBody(result)
                val headers = HttpHeaders()
                headers.set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)

                val responseEntity = when (result.errorCode as RenderTemplateError) {
                    RenderTemplateError.NOT_FOUND_ERROR -> ResponseEntity(
                        errorStreamingBody,
                        headers,
                        HttpStatus.NOT_FOUND
                    )

                    else -> ResponseEntity(
                        errorStreamingBody,
                        headers,
                        HttpStatus.INTERNAL_SERVER_ERROR
                    )
                }

                return responseEntity
            }
        }
    }
}
