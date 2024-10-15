package com.aamdigital.aambackendservice.export.usecase

import com.aamdigital.aambackendservice.common.WebClientTestBase
import com.aamdigital.aambackendservice.domain.DomainReference
import com.aamdigital.aambackendservice.domain.TestErrorCode
import com.aamdigital.aambackendservice.domain.UseCaseOutcome
import com.aamdigital.aambackendservice.error.ExternalSystemException
import com.aamdigital.aambackendservice.error.NetworkException
import com.aamdigital.aambackendservice.error.NotFoundException
import com.aamdigital.aambackendservice.export.core.RenderTemplateError
import com.aamdigital.aambackendservice.export.core.RenderTemplateRequest
import com.aamdigital.aambackendservice.export.core.RenderTemplateUseCase
import com.aamdigital.aambackendservice.export.core.TemplateExport
import com.aamdigital.aambackendservice.export.core.TemplateStorage
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.readValue
import okhttp3.Headers.Companion.toHeaders
import okhttp3.mockwebserver.MockResponse
import okio.Buffer
import okio.source
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.reset
import org.mockito.kotlin.whenever
import org.springframework.web.client.RestClientException
import java.io.ByteArrayInputStream
import java.io.File
import java.net.SocketTimeoutException


@ExtendWith(MockitoExtension::class)
class DefaultRenderTemplateUseCaseTest : WebClientTestBase() {

    @Mock
    lateinit var templateStorage: TemplateStorage

    private lateinit var service: RenderTemplateUseCase

    override fun setUp() {
        super.setUp()
        reset(templateStorage)

        service = DefaultRenderTemplateUseCase(
            renderClient = restClient,
            objectMapper = objectMapper,
            templateStorage = templateStorage,
        )
    }

    @Test
    fun `should return Failure when fetchTemplate returns an error`() {
        // given
        val templateRef = DomainReference("some-id")
        val bodyData: JsonNode = objectMapper.readValue(
            """
                {"foo":"bar"}
            """.trimIndent()
        )

        val exception = ExternalSystemException(
            message = "fetchTemplate error",
            code = TestErrorCode.TEST_EXCEPTION,
            cause = null
        )

        whenever(templateStorage.fetchTemplate(templateRef)).thenAnswer {
            throw exception
        }

        // when
        val response = service.run(
            RenderTemplateRequest(
                templateRef, bodyData
            )
        )

        // then
        assertThat(response).isInstanceOf(UseCaseOutcome.Failure::class.java)
        assertEquals(
            RenderTemplateError.FETCH_TEMPLATE_FAILED_ERROR, (response as UseCaseOutcome.Failure).errorCode
        )
    }

    @Test
    fun `should return Failure when json response could not be parsed`() {
        // given
        val templateRef = DomainReference("some-id")
        val bodyData: JsonNode = objectMapper.readValue(
            """
                {"foo":"bar"}
            """.trimIndent()
        )
        val templateExport = TemplateExport(
            id = "export-id",
            templateId = "export-template-id",
            targetFileName = "target_file_name.file",
            title = "export-title",
            description = "export-description",
            applicableForEntityTypes = emptyList()
        )

        whenever(templateStorage.fetchTemplate(templateRef)).thenReturn(templateExport)
        mockWebServer.enqueue(MockResponse().setBody("invalid json"))

        // when
        val response = service.run(
            RenderTemplateRequest(
                templateRef, bodyData
            )
        )

        // then
        assertThat(response).isInstanceOf(UseCaseOutcome.Failure::class.java)
        assertEquals(
            RenderTemplateError.PARSE_RESPONSE_ERROR, (response as UseCaseOutcome.Failure).errorCode
        )
    }

    @Test
    fun `should return Failure with NOT_FOUND_ERROR when fetchTemplate throws NotFoundException exception`() {
        // given
        val templateRef = DomainReference("some-id")
        val bodyData: JsonNode = objectMapper.readValue(
            """
                {"foo":"bar"}
            """.trimIndent()
        )

        whenever(templateStorage.fetchTemplate(templateRef)).thenAnswer {
            throw NotFoundException(
                code = TestErrorCode.TEST_EXCEPTION,
            )
        }

        // when
        val response = service.run(
            RenderTemplateRequest(
                templateRef, bodyData
            )
        )

        // then
        assertThat(response).isInstanceOf(UseCaseOutcome.Failure::class.java)
        assertEquals(
            RenderTemplateError.NOT_FOUND_ERROR, (response as UseCaseOutcome.Failure).errorCode
        )
    }

    @Test
    fun `should return Success with DataBuffer`() {
        // given
        val templateRef = DomainReference("some-id")
        val bodyData: JsonNode = objectMapper.readValue(
            """
                {"foo":"bar"}
            """.trimIndent()
        )
        val templateExport = TemplateExport(
            id = "export-id",
            templateId = "export-template-id",
            targetFileName = "target_file_name.file",
            title = "export-title",
            description = "export-description",
            applicableForEntityTypes = emptyList()
        )

        whenever(templateStorage.fetchTemplate(templateRef)).thenReturn(templateExport)

        mockWebServer.enqueue(
            MockResponse().setBody(
                """
            {
                "success": true,
                "data": {
                    "renderId": "some-render-id"
                }
            }
                """.trimIndent()
            )
        )

        val buffer = Buffer()
        buffer.writeAll(File("src/test/resources/files/pdf-test-file-1.pdf").source())

        mockWebServer.enqueue(
            MockResponse()
                .setHeaders(
                    mapOf(
                        "Content-Type" to "application/pdf",
                        "Content-Length" to buffer.size.toString(),
                        "Cache-Control" to "no-cache, no-store, max-age=0, must-revalidate",
                    ).toHeaders()
                )
                .setBody(buffer)

        )

        // when
        val response = service.run(
            RenderTemplateRequest(
                templateRef, bodyData
            )
        )

        // then
        assertThat(response).isInstanceOf(UseCaseOutcome.Success::class.java)

        assertThat((response as UseCaseOutcome.Success).data.file).isInstanceOf(ByteArrayInputStream::class.java)
    }

    @Test
    fun `should return Failure with parsed error message`() {
        // given
        val templateRef = DomainReference("some-id")
        val bodyData: JsonNode = objectMapper.readValue(
            """
                {"foo":"bar"}
            """.trimIndent()
        )
        val templateExport = TemplateExport(
            id = "export-id",
            templateId = "export-template-id",
            targetFileName = "target_file_name.file",
            title = "export-title",
            description = "export-description",
            applicableForEntityTypes = emptyList()
        )

        whenever(templateStorage.fetchTemplate(templateRef)).thenReturn(templateExport)

        mockWebServer.enqueue(
            MockResponse().setBody(
                """
            {
                "success": false,
                "error": "this is an error message"
            }
                """.trimIndent()
            )
        )

        // when
        val response = service.run(
            RenderTemplateRequest(
                templateRef, bodyData
            )
        )

        // then
        assertThat(response).isInstanceOf(UseCaseOutcome.Failure::class.java)

        assertEquals("this is an error message", (response as UseCaseOutcome.Failure).errorMessage)
    }

    @Test
    fun `should return Failure when createRenderRequest runs in timeout`() {
        // given
        val templateRef = DomainReference("some-id")
        val bodyData: JsonNode = objectMapper.readValue(
            """
                {"foo":"bar"}
            """.trimIndent()
        )
        val templateExport = TemplateExport(
            id = "export-id",
            templateId = "export-template-id",
            targetFileName = "target_file_name.file",
            title = "export-title",
            description = "export-description",
            applicableForEntityTypes = emptyList()
        )

        whenever(templateStorage.fetchTemplate(templateRef)).thenReturn(templateExport)

        // when
        val response = service.run(
            RenderTemplateRequest(
                templateRef, bodyData
            )
        )

        // then
        assertThat(response).isInstanceOf(UseCaseOutcome.Failure::class.java)

        assertThat((response as UseCaseOutcome.Failure).cause)
            .isInstanceOf(ExternalSystemException::class.java)

        assertThat(response.cause?.cause)
            .isInstanceOf(RestClientException::class.java)

        assertThat(response.errorCode)
            .isEqualTo(RenderTemplateError.CREATE_RENDER_REQUEST_FAILED_ERROR)
    }

    @Test
    fun `should return Failure when fetchRenderIdRequest fails`() {
        // given
        val templateRef = DomainReference("some-id")
        val bodyData: JsonNode = objectMapper.readValue(
            """
                {"foo":"bar"}
            """.trimIndent()
        )
        val templateExport = TemplateExport(
            id = "export-id",
            templateId = "export-template-id",
            targetFileName = "target_file_name.file",
            title = "export-title",
            description = "export-description",
            applicableForEntityTypes = emptyList()
        )

        whenever(templateStorage.fetchTemplate(templateRef)).thenReturn(templateExport)

        mockWebServer.enqueue(
            MockResponse().setBody(
                """
            {
                "success": true,
                "data": {
                    "renderId": "some-render-id"
                }
            }
                """.trimIndent()
            )
        )

        // when
        val response = service.run(
            RenderTemplateRequest(
                templateRef, bodyData
            )
        )

        // then
        assertThat(response).isInstanceOf(UseCaseOutcome.Failure::class.java)

        assertThat((response as UseCaseOutcome.Failure).cause)
            .isInstanceOf(NetworkException::class.java)

        assertThat(response.cause?.cause)
            .isInstanceOf(SocketTimeoutException::class.java)

        assertThat(response.errorCode)
            .isEqualTo(RenderTemplateError.FETCH_RENDER_ID_REQUEST_FAILED_ERROR)
    }
}
