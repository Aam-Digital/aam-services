package com.aamdigital.aambackendservice.export.controller

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.ThrowableProxy
import ch.qos.logback.core.read.ListAppender
import com.aamdigital.aambackendservice.common.domain.DomainUseCase
import com.aamdigital.aambackendservice.common.domain.UseCaseOutcome
import com.aamdigital.aambackendservice.export.core.CreateTemplateError
import com.aamdigital.aambackendservice.export.core.CreateTemplateUseCase
import com.aamdigital.aambackendservice.export.core.FetchTemplateData
import com.aamdigital.aambackendservice.export.core.FetchTemplateError
import com.aamdigital.aambackendservice.export.core.FetchTemplateUseCase
import com.aamdigital.aambackendservice.export.core.RenderTemplateBatchData
import com.aamdigital.aambackendservice.export.core.RenderTemplateBatchError
import com.aamdigital.aambackendservice.export.core.RenderTemplateBatchUseCase
import com.aamdigital.aambackendservice.export.core.RenderTemplateData
import com.aamdigital.aambackendservice.export.core.RenderTemplateError
import com.aamdigital.aambackendservice.export.core.RenderTemplateUseCase
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

@ExtendWith(MockitoExtension::class)
class TemplateExportControllerTest {
    @Mock
    private lateinit var createTemplateUseCase: CreateTemplateUseCase

    @Mock
    private lateinit var fetchTemplateUseCase: FetchTemplateUseCase

    @Mock
    private lateinit var renderTemplateUseCase: RenderTemplateUseCase

    @Mock
    private lateinit var renderTemplateBatchUseCase: RenderTemplateBatchUseCase

    private lateinit var mockMvc: MockMvc

    private lateinit var logger: Logger
    private lateinit var logAppender: ListAppender<ILoggingEvent>

    companion object {
        private const val TEMPLATE_ID = "TemplateExport:1"

        // a rendered file is binary; the high bytes also show that nothing re-encodes the payload
        private val FILE_CONTENT = byteArrayOf(0x25, 0x50, 0x44, 0x46, -0x11, -0x42, 0x0a)
    }

    @BeforeEach
    fun setUp() {
        mockMvc =
            MockMvcBuilders
                .standaloneSetup(
                    TemplateExportController(
                        createTemplateUseCase = createTemplateUseCase,
                        fetchTemplateUseCase = fetchTemplateUseCase,
                        renderTemplateUseCase = renderTemplateUseCase,
                        renderTemplateBatchUseCase = renderTemplateBatchUseCase,
                        objectMapper = ObjectMapper()
                    )
                ).build()

        logger = LoggerFactory.getLogger(TemplateExportController::class.java) as Logger
        logAppender = ListAppender<ILoggingEvent>().apply { start() }
        logger.addAppender(logAppender)
    }

    @AfterEach
    fun tearDown() {
        logger.detachAppender(logAppender)
    }

    private fun loggedWarning(): ILoggingEvent {
        val warnings = logAppender.list.filter { it.level == Level.WARN }
        assertThat(warnings).hasSize(1)
        return warnings.single()
    }

    private fun ILoggingEvent.loggedCause(): Throwable? = (throwableProxy as ThrowableProxy?)?.throwable

    private fun fileHeaders(): HttpHeaders =
        HttpHeaders().apply {
            contentType = MediaType.APPLICATION_OCTET_STREAM
            set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=export.pdf")
        }

    @Test
    fun `serves a template on the request thread instead of starting an async response`() {
        // Given - a StreamingResponseBody would hand the write to an async task, which lets the
        // container recycle the request underneath the writer
        whenever(fetchTemplateUseCase.run(any()))
            .thenReturn(
                UseCaseOutcome.Success(
                    FetchTemplateData(
                        file = FILE_CONTENT.inputStream(),
                        responseHeaders = fileHeaders()
                    )
                )
            )

        // When
        val result = mockMvc.perform(get("/v1/export/template/$TEMPLATE_ID"))

        // Then
        result
            .andExpect(status().isOk)
            .andExpect(request().asyncNotStarted())
    }

    @Test
    fun `returns the template bytes unaltered, with the use case headers and no content length`() {
        // Given
        whenever(fetchTemplateUseCase.run(any()))
            .thenReturn(
                UseCaseOutcome.Success(
                    FetchTemplateData(
                        file = FILE_CONTENT.inputStream(),
                        responseHeaders = fileHeaders()
                    )
                )
            )

        // When
        val response =
            mockMvc
                .perform(get("/v1/export/template/$TEMPLATE_ID"))
                .andReturn()
                .response

        // Then
        assertThat(response.contentAsByteArray).isEqualTo(FILE_CONTENT)
        // A content length would mean the resource was drained to measure it, leaving an empty body
        assertThat(response.getHeader(HttpHeaders.CONTENT_LENGTH)).isNull()
        assertThat(response.getHeader(HttpHeaders.CONTENT_DISPOSITION))
            .isEqualTo("attachment; filename=export.pdf")
    }

    @Test
    fun `renders a template without starting an async response`() {
        // Given
        whenever(renderTemplateUseCase.run(any()))
            .thenReturn(
                UseCaseOutcome.Success(
                    RenderTemplateData(
                        file = FILE_CONTENT.inputStream(),
                        responseHeaders = fileHeaders()
                    )
                )
            )

        // When
        val result =
            mockMvc.perform(
                post("/v1/export/render/$TEMPLATE_ID")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"Bärbel"}""")
            )

        // Then
        result
            .andExpect(status().isOk)
            .andExpect(request().asyncNotStarted())
        assertThat(result.andReturn().response.contentAsByteArray).isEqualTo(FILE_CONTENT)
    }

    @Test
    fun `renders a batch without starting an async response`() {
        // Given
        whenever(renderTemplateBatchUseCase.run(any()))
            .thenReturn(
                UseCaseOutcome.Success(
                    RenderTemplateBatchData(
                        file = FILE_CONTENT.inputStream(),
                        responseHeaders = fileHeaders()
                    )
                )
            )

        // When
        val result =
            mockMvc.perform(
                post("/v1/export/render-batch/$TEMPLATE_ID")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""[{"name":"Bärbel"}]""")
            )

        // Then
        result
            .andExpect(status().isOk)
            .andExpect(request().asyncNotStarted())
        assertThat(result.andReturn().response.contentAsByteArray).isEqualTo(FILE_CONTENT)
    }

    @Test
    fun `answers with a json error body when the template is unknown`() {
        // Given
        whenever(fetchTemplateUseCase.run(any()))
            .thenReturn(
                UseCaseOutcome.Failure(
                    errorCode = FetchTemplateError.NOT_FOUND_ERROR,
                    errorMessage = "Template not found"
                )
            )

        // When
        val response =
            mockMvc
                .perform(get("/v1/export/template/$TEMPLATE_ID"))
                .andReturn()
                .response

        // Then
        assertThat(response.status).isEqualTo(404)
        assertThat(response.contentType).isEqualTo(MediaType.APPLICATION_JSON_VALUE)
        assertThat(response.contentAsString).contains("NOT_FOUND_ERROR", "Template not found")
    }

    @Test
    fun `rejects an unsupported batch mode with a json error body`() {
        // When
        val response =
            mockMvc
                .perform(
                    post("/v1/export/render-batch/$TEMPLATE_ID")
                        .param("mode", "unsupported")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""[{"name":"Bärbel"}]""")
                ).andReturn()
                .response

        // Then
        assertThat(response.status).isEqualTo(400)
        assertThat(response.contentAsString).contains("INVALID_MODE", "Allowed values: zip, combined.")
    }

    @Test
    fun `logs a failed template upload at WARN with its error code and cause`() {
        // Given
        val cause = IllegalStateException("template engine unreachable")
        whenever(createTemplateUseCase.run(any()))
            .thenReturn(
                UseCaseOutcome.Failure(
                    errorCode = CreateTemplateError.CREATE_TEMPLATE_REQUEST_FAILED_ERROR,
                    errorMessage = "Could not upload the template",
                    cause = cause
                )
            )

        // When
        val response =
            mockMvc
                .perform(
                    multipart("/v1/export/template")
                        .file(MockMultipartFile("template", "template.docx", null, FILE_CONTENT))
                ).andReturn()
                .response

        // Then
        assertThat(response.status).isEqualTo(500)
        val warning = loggedWarning()
        assertThat(warning.formattedMessage)
            .contains("CREATE_TEMPLATE_REQUEST_FAILED_ERROR", "Could not upload the template")
        assertThat(warning.loggedCause()).isSameAs(cause)
    }

    @Test
    fun `logs a failed render at WARN with the template id, error code and cause`() {
        // Given
        val cause = IllegalStateException("template engine unreachable")
        whenever(renderTemplateUseCase.run(any()))
            .thenReturn(
                UseCaseOutcome.Failure(
                    errorCode = RenderTemplateError.CREATE_RENDER_REQUEST_FAILED_ERROR,
                    errorMessage = "Could not render the template",
                    cause = cause
                )
            )

        // When
        val response =
            mockMvc
                .perform(
                    post("/v1/export/render/$TEMPLATE_ID")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"name":"Bärbel"}""")
                ).andReturn()
                .response

        // Then
        assertThat(response.status).isEqualTo(500)
        val warning = loggedWarning()
        assertThat(warning.formattedMessage)
            .contains(TEMPLATE_ID, "CREATE_RENDER_REQUEST_FAILED_ERROR", "Could not render the template")
        assertThat(warning.loggedCause()).isSameAs(cause)
    }

    @Test
    fun `logs a rejected batch at WARN even though it answers with a client error`() {
        // Given
        whenever(renderTemplateBatchUseCase.run(any()))
            .thenReturn(
                UseCaseOutcome.Failure(
                    errorCode = RenderTemplateBatchError.EMPTY_DATA_LIST_ERROR,
                    errorMessage = "Request 'data' array must not be empty."
                )
            )

        // When
        val response =
            mockMvc
                .perform(
                    post("/v1/export/render-batch/$TEMPLATE_ID")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"data":[]}""")
                ).andReturn()
                .response

        // Then
        assertThat(response.status).isEqualTo(400)
        assertThat(loggedWarning().formattedMessage).contains(TEMPLATE_ID, "EMPTY_DATA_LIST_ERROR")
    }

    @Test
    fun `logs a failed template fetch with its cause even when the error code is not an export one`() {
        // Given - the code DomainUseCase gives an exception it does not know
        val cause = IllegalStateException("unexpected response")
        whenever(fetchTemplateUseCase.run(any()))
            .thenReturn(
                UseCaseOutcome.Failure(
                    errorCode = DomainUseCase.DomainError.UNHANDLED_EXCEPTION_IN_USE_CASE,
                    errorMessage = "unexpected response",
                    cause = cause
                )
            )

        // When - whatever the controller answers to a code it cannot map, it must not lose the cause
        catchThrowable { mockMvc.perform(get("/v1/export/template/$TEMPLATE_ID")) }

        // Then
        val warning = loggedWarning()
        assertThat(warning.formattedMessage).contains(TEMPLATE_ID, "UNHANDLED_EXCEPTION_IN_USE_CASE")
        assertThat(warning.loggedCause()).isSameAs(cause)
    }
}
