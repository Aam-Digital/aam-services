package com.aamdigital.aambackendservice.export.controller

import com.aamdigital.aambackendservice.common.domain.DomainReference
import com.aamdigital.aambackendservice.common.domain.UseCaseOutcome.Failure
import com.aamdigital.aambackendservice.common.domain.UseCaseOutcome.Success
import com.aamdigital.aambackendservice.common.error.HttpErrorDto
import com.aamdigital.aambackendservice.export.ConditionalOnExportApiEnabled
import com.aamdigital.aambackendservice.export.core.CreateTemplateError
import com.aamdigital.aambackendservice.export.core.CreateTemplateRequest
import com.aamdigital.aambackendservice.export.core.CreateTemplateUseCase
import com.aamdigital.aambackendservice.export.core.FetchTemplateError
import com.aamdigital.aambackendservice.export.core.FetchTemplateRequest
import com.aamdigital.aambackendservice.export.core.FetchTemplateUseCase
import com.aamdigital.aambackendservice.export.core.RenderTemplateBatchError
import com.aamdigital.aambackendservice.export.core.RenderTemplateBatchMode
import com.aamdigital.aambackendservice.export.core.RenderTemplateBatchRequest
import com.aamdigital.aambackendservice.export.core.RenderTemplateBatchUseCase
import com.aamdigital.aambackendservice.export.core.RenderTemplateError
import com.aamdigital.aambackendservice.export.core.RenderTemplateRequest
import com.aamdigital.aambackendservice.export.core.RenderTemplateUseCase
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.core.io.InputStreamResource
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
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

sealed interface TemplateExportControllerResponse {
    /**
     * @param templateId The external identifier of the implementing TemplateEngine
     */
    data class CreateTemplateControllerResponse(
        val templateId: String
    ) : TemplateExportControllerResponse

    class ErrorControllerResponse(
        errorCode: String,
        errorMessage: String
    ) : HttpErrorDto(
            errorCode,
            errorMessage
        ),
        TemplateExportControllerResponse
}

/**
 * REST controller responsible for handling export operations related to templates.
 * Provides endpoints for checking status, posting a new template, and rendering a template.
 *
 * In Aam, this API is especially used for generating PDFs for an entity.
 *
 * Every endpoint that answers with a file returns an [InputStreamResource] rather than a
 * `org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody`. A
 * StreamingResponseBody is written from an async task while the container owns the request
 * lifecycle, so Tomcat can recycle the request underneath the writer - on the async timeout (30s
 * by default, which by itself truncates slow downloads) or when the client goes away. Both threads
 * then touch the same `MimeHeaders`, which is not thread safe, and the response commit fails with
 * a NullPointerException from inside Tomcat, sometimes taking whichever request next reuses the
 * recycled objects with it. Neither Tomcat nor Spring treats that as fixable on their side
 * (spring-framework#33439 was closed as not planned).
 *
 * Returning a Resource keeps the copy on the request thread, where the container cannot recycle
 * anything until the handler returns. Virtual threads are enabled, so blocking that thread for the
 * length of a download is cheap, and a client that disappears mid-download surfaces as a
 * `ClientAbortException` that Spring's `DefaultHandlerExceptionResolver` logs at DEBUG.
 * `ResourceHttpMessageConverter` copies the stream verbatim and closes it afterwards.
 *
 * @param createTemplateUseCase Use case for creating a new template.
 * @param fetchTemplateUseCase Use case for fetching an existing template (file).
 * @param renderTemplateUseCase Use case for rendering an existing template.
 */
@RestController
@RequestMapping("/v1/export")
@ConditionalOnExportApiEnabled
@Validated
class TemplateExportController(
    private val createTemplateUseCase: CreateTemplateUseCase,
    private val fetchTemplateUseCase: FetchTemplateUseCase,
    private val renderTemplateUseCase: RenderTemplateUseCase,
    private val renderTemplateBatchUseCase: RenderTemplateBatchUseCase,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    private fun getErrorEntity(
        errorCode: String,
        errorMessage: String,
        status: HttpStatus = HttpStatus.INTERNAL_SERVER_ERROR
    ): ResponseEntity<TemplateExportControllerResponse> =
        ResponseEntity
            .status(status)
            .body(
                TemplateExportControllerResponse.ErrorControllerResponse(
                    errorMessage = errorMessage,
                    errorCode = errorCode
                )
            )

    /*
     * Keeps the error responses the same body type as the success ones, so no converter is needed.
     */
    private fun getErrorBody(result: Failure<*>) =
        getErrorBody(
            errorCode = result.errorCode.toString(),
            errorMessage = result.errorMessage
        )

    private fun getErrorBody(
        errorCode: String,
        errorMessage: String
    ) = InputStreamResource(
        objectMapper
            .writeValueAsString(
                TemplateExportControllerResponse.ErrorControllerResponse(
                    errorCode = errorCode,
                    errorMessage = errorMessage
                )
            ).byteInputStream()
    )

    @PostMapping("/template")
    fun postTemplate(
        @RequestPart("template") file: MultipartFile
    ): ResponseEntity<TemplateExportControllerResponse> {
        logger.trace("trying to create new Template")

        val result =
            createTemplateUseCase
                .run(
                    CreateTemplateRequest(
                        file = file
                    )
                )

        return when (result) {
            is Success -> {
                val response =
                    TemplateExportControllerResponse.CreateTemplateControllerResponse(
                        templateId = result.data.templateRef.id
                    )

                logger.trace(
                    "[TemplateExportController.postTemplate()] success response: {}",
                    response.toString()
                )

                ResponseEntity.ok(response)
            }

            is Failure -> {
                val responseEntity =
                    when (result.errorCode as CreateTemplateError) {
                        else ->
                            getErrorEntity(
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
        @PathVariable templateId: String
    ): ResponseEntity<InputStreamResource> {
        val result =
            fetchTemplateUseCase.run(
                FetchTemplateRequest(
                    templateRef = DomainReference(templateId)
                )
            )

        return when (result) {
            is Success -> {
                val responseBody = InputStreamResource(result.data.file)

                logger.trace(
                    "[TemplateExportController.fetchTemplate()] success response: (template file)"
                )

                ResponseEntity(
                    responseBody,
                    result.data.responseHeaders,
                    HttpStatus.OK
                )
            }

            is Failure -> {
                val errorBody = getErrorBody(result)
                val headers = HttpHeaders()
                headers.set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)

                val responseEntity =
                    when (result.errorCode as FetchTemplateError) {
                        FetchTemplateError.NOT_FOUND_ERROR ->
                            ResponseEntity(
                                errorBody,
                                headers,
                                HttpStatus.NOT_FOUND
                            )

                        else ->
                            ResponseEntity(
                                errorBody,
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
        @RequestBody templateData: JsonNode
    ): ResponseEntity<InputStreamResource> {
        val result =
            renderTemplateUseCase.run(
                RenderTemplateRequest(
                    templateRef = DomainReference(templateId),
                    bodyData = templateData
                )
            )

        return when (result) {
            is Success -> {
                val responseBody = InputStreamResource(result.data.file)

                logger.trace(
                    "[TemplateExportController.renderTemplate()] success response: (rendered file)"
                )

                ResponseEntity(
                    responseBody,
                    result.data.responseHeaders,
                    HttpStatus.OK
                )
            }

            is Failure -> {
                val errorBody = getErrorBody(result)
                val headers = HttpHeaders()
                headers.set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)

                val responseEntity =
                    when (result.errorCode as RenderTemplateError) {
                        RenderTemplateError.NOT_FOUND_ERROR ->
                            ResponseEntity(
                                errorBody,
                                headers,
                                HttpStatus.NOT_FOUND
                            )

                        else ->
                            ResponseEntity(
                                errorBody,
                                headers,
                                HttpStatus.INTERNAL_SERVER_ERROR
                            )
                    }

                return responseEntity
            }
        }
    }

    /**
     * Render a template for an array of records and return either a ZIP of N independently
     * rendered files (default, `mode=zip`) or a single document produced by Carbone with the
     * array forwarded as-is (`mode=combined` — requires a template that uses array placeholders).
     */
    @PostMapping("/render-batch/{templateId}")
    fun renderTemplateBatch(
        @PathVariable templateId: String,
        @RequestParam(name = "mode", required = false, defaultValue = "zip") mode: String,
        @RequestBody templateData: JsonNode
    ): ResponseEntity<InputStreamResource> {
        val parsedMode =
            when (mode.lowercase()) {
                "zip" -> {
                    RenderTemplateBatchMode.ZIP
                }

                "combined" -> {
                    RenderTemplateBatchMode.COMBINED
                }

                else -> {
                    val headers = HttpHeaders()
                    headers.set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    return ResponseEntity(
                        getErrorBody(
                            errorCode = "INVALID_MODE",
                            errorMessage = "Unsupported mode '$mode'. Allowed values: zip, combined."
                        ),
                        headers,
                        HttpStatus.BAD_REQUEST
                    )
                }
            }

        val result =
            renderTemplateBatchUseCase.run(
                RenderTemplateBatchRequest(
                    templateRef = DomainReference(templateId),
                    bodyData = templateData,
                    mode = parsedMode
                )
            )

        return when (result) {
            is Success -> {
                val responseHeaders = HttpHeaders().apply { putAll(result.data.responseHeaders) }

                val responseBody = InputStreamResource(result.data.file)

                logger.trace(
                    "[TemplateExportController.renderTemplateBatch()] success response: (batch file)"
                )

                ResponseEntity(
                    responseBody,
                    responseHeaders,
                    HttpStatus.OK
                )
            }

            is Failure -> {
                val errorBody = getErrorBody(result)
                val headers = HttpHeaders()
                headers.set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)

                val status =
                    when (result.errorCode as RenderTemplateBatchError) {
                        RenderTemplateBatchError.NOT_FOUND_ERROR -> HttpStatus.NOT_FOUND

                        RenderTemplateBatchError.EMPTY_DATA_LIST_ERROR,
                        RenderTemplateBatchError.INVALID_DATA_SHAPE_ERROR -> HttpStatus.BAD_REQUEST

                        RenderTemplateBatchError.BATCH_REJECTED_ERROR -> HttpStatus.UNPROCESSABLE_ENTITY

                        else -> HttpStatus.INTERNAL_SERVER_ERROR
                    }

                ResponseEntity(errorBody, headers, status)
            }
        }
    }
}
