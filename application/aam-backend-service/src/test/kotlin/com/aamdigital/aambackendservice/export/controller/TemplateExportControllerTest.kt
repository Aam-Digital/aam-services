package com.aamdigital.aambackendservice.export.controller

import com.aamdigital.aambackendservice.common.domain.UseCaseOutcome
import com.aamdigital.aambackendservice.export.core.CreateTemplateUseCase
import com.aamdigital.aambackendservice.export.core.FetchTemplateData
import com.aamdigital.aambackendservice.export.core.FetchTemplateError
import com.aamdigital.aambackendservice.export.core.FetchTemplateUseCase
import com.aamdigital.aambackendservice.export.core.RenderTemplateBatchData
import com.aamdigital.aambackendservice.export.core.RenderTemplateBatchUseCase
import com.aamdigital.aambackendservice.export.core.RenderTemplateData
import com.aamdigital.aambackendservice.export.core.RenderTemplateUseCase
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
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
    }

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
}
