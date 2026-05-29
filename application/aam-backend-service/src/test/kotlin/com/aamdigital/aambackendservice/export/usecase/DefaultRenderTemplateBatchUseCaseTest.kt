package com.aamdigital.aambackendservice.export.usecase

import com.aamdigital.aambackendservice.common.WebClientTestBase
import com.aamdigital.aambackendservice.common.domain.DomainReference
import com.aamdigital.aambackendservice.common.domain.UseCaseOutcome
import com.aamdigital.aambackendservice.export.core.RenderTemplateBatchError
import com.aamdigital.aambackendservice.export.core.RenderTemplateBatchMode
import com.aamdigital.aambackendservice.export.core.RenderTemplateBatchRequest
import com.aamdigital.aambackendservice.export.core.RenderTemplateBatchUseCase
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
import java.io.File

@ExtendWith(MockitoExtension::class)
class DefaultRenderTemplateBatchUseCaseTest : WebClientTestBase() {
    @Mock
    lateinit var templateStorage: TemplateStorage

    private lateinit var service: RenderTemplateBatchUseCase

    override fun setUp() {
        super.setUp()
        reset(templateStorage)

        service =
            DefaultRenderTemplateBatchUseCase(
                renderClient = restClient,
                objectMapper = objectMapper,
                templateStorage = templateStorage
            )
    }

    private fun template() =
        TemplateExport(
            id = "export-id",
            templateId = "export-template-id",
            targetFileName = "child-report.pdf",
            title = "Child Report",
            description = "report",
            applicableForEntityTypes = emptyList()
        )

    private fun enqueueRenderAndFile(
        renderId: String,
        contentType: String
    ) {
        mockWebServer.enqueue(
            MockResponse().setBody(
                """{"success":true,"data":{"renderId":"$renderId"}}"""
            )
        )
        val buffer = Buffer()
        buffer.writeAll(File("src/test/resources/files/pdf-test-file-1.pdf").source())
        mockWebServer.enqueue(
            MockResponse()
                .setHeaders(
                    mapOf(
                        "Content-Type" to contentType,
                        "Content-Disposition" to "attachment; filename=\"$renderId\"",
                        "Content-Length" to buffer.size.toString()
                    ).toHeaders()
                ).setBody(buffer)
        )
    }

    @Test
    fun `should return INVALID_DATA_SHAPE_ERROR when 'data' is not an array`() {
        val bodyData: JsonNode = objectMapper.readValue("""{"data": {"foo":"bar"}}""")

        val response =
            service.run(
                RenderTemplateBatchRequest(
                    templateRef = DomainReference("some-id"),
                    bodyData = bodyData,
                    mode = RenderTemplateBatchMode.ZIP
                )
            )

        assertThat(response).isInstanceOf(UseCaseOutcome.Failure::class.java)
        assertEquals(
            RenderTemplateBatchError.INVALID_DATA_SHAPE_ERROR,
            (response as UseCaseOutcome.Failure).errorCode
        )
    }

    @Test
    fun `should return EMPTY_DATA_LIST_ERROR when data array is empty`() {
        val bodyData: JsonNode = objectMapper.readValue("""{"data": []}""")

        val response =
            service.run(
                RenderTemplateBatchRequest(
                    templateRef = DomainReference("some-id"),
                    bodyData = bodyData,
                    mode = RenderTemplateBatchMode.ZIP
                )
            )

        assertThat(response).isInstanceOf(UseCaseOutcome.Failure::class.java)
        assertEquals(
            RenderTemplateBatchError.EMPTY_DATA_LIST_ERROR,
            (response as UseCaseOutcome.Failure).errorCode
        )
    }

    @Test
    fun `ZIP mode should forward batchSplitBy and batchOutput zip to Carbone and stream result`() {
        val templateRef = DomainReference("some-id")
        val bodyData: JsonNode =
            objectMapper.readValue(
                """{"convertTo":"pdf","data":[{"name":"Alice"},{"name":"Bob"}]}"""
            )
        whenever(templateStorage.fetchTemplate(templateRef)).thenReturn(template())
        enqueueRenderAndFile(renderId = "render-zip-1", contentType = "application/zip")

        val response =
            service.run(
                RenderTemplateBatchRequest(
                    templateRef = templateRef,
                    bodyData = bodyData,
                    mode = RenderTemplateBatchMode.ZIP
                )
            )

        assertThat(response).isInstanceOf(UseCaseOutcome.Success::class.java)
        val data = (response as UseCaseOutcome.Success).data
        assertThat(data.responseHeaders.contentType?.toString()).isEqualTo("application/zip")

        // first recorded request is POST /render/{templateId} with our injected batch options
        val renderRequest = mockWebServer.takeRequest()
        assertThat(renderRequest.path).isEqualTo("/render/export-template-id")
        val sentBody: Map<String, Any?> = objectMapper.readValue(renderRequest.body.readUtf8())
        assertThat(sentBody["batchSplitBy"]).isEqualTo("d")
        assertThat(sentBody["batchOutput"]).isEqualTo("zip")
        assertThat(sentBody["reportName"]).isEqualTo("child-report.pdf")
        assertThat(sentBody["data"]).isInstanceOf(List::class.java)
    }

    @Test
    fun `COMBINED mode should forward batchOutput pdf to Carbone`() {
        val templateRef = DomainReference("some-id")
        val bodyData: JsonNode =
            objectMapper.readValue(
                """{"data":[{"name":"Alice"},{"name":"Bob"}]}"""
            )
        whenever(templateStorage.fetchTemplate(templateRef)).thenReturn(template())
        enqueueRenderAndFile(renderId = "render-pdf-1", contentType = "application/pdf")

        val response =
            service.run(
                RenderTemplateBatchRequest(
                    templateRef = templateRef,
                    bodyData = bodyData,
                    mode = RenderTemplateBatchMode.COMBINED
                )
            )

        assertThat(response).isInstanceOf(UseCaseOutcome.Success::class.java)
        val data = (response as UseCaseOutcome.Success).data
        assertThat(data.responseHeaders.contentType?.toString()).isEqualTo("application/pdf")

        val renderRequest = mockWebServer.takeRequest()
        val sentBody: Map<String, Any?> = objectMapper.readValue(renderRequest.body.readUtf8())
        assertThat(sentBody["batchSplitBy"]).isEqualTo("d")
        assertThat(sentBody["batchOutput"]).isEqualTo("pdf")
    }

    @Test
    fun `should return BATCH_REJECTED_ERROR with Carbone's message when batch is over the limit`() {
        val templateRef = DomainReference("some-id")
        val bodyData: JsonNode =
            objectMapper.readValue("""{"data":[{"name":"X"},{"name":"Y"}]}""")
        whenever(templateStorage.fetchTemplate(templateRef)).thenReturn(template())

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody(
                    """{"success":false,"error":"Unable to generate the document. Cannot process more than 200 documents","code":"w101","data":{"renderId":""}}"""
                )
        )

        val response =
            service.run(
                RenderTemplateBatchRequest(
                    templateRef = templateRef,
                    bodyData = bodyData,
                    mode = RenderTemplateBatchMode.ZIP
                )
            )

        assertThat(response).isInstanceOf(UseCaseOutcome.Failure::class.java)
        val failure = response as UseCaseOutcome.Failure
        assertEquals(RenderTemplateBatchError.BATCH_REJECTED_ERROR, failure.errorCode)
        assertThat(failure.errorMessage).contains("Cannot process more than 200 documents")
    }

    @Test
    fun `should return BATCH_REJECTED_ERROR when batch processing is deactivated on the Carbone instance`() {
        val templateRef = DomainReference("some-id")
        val bodyData: JsonNode =
            objectMapper.readValue("""{"data":[{"name":"X"}]}""")
        whenever(templateStorage.fetchTemplate(templateRef)).thenReturn(template())

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody(
                    """{"success":false,"error":"Unable to generate the document. Batch processing deactivated. nbReportMaxPerBatch = 0","code":"w101","data":{"renderId":""}}"""
                )
        )

        val response =
            service.run(
                RenderTemplateBatchRequest(
                    templateRef = templateRef,
                    bodyData = bodyData,
                    mode = RenderTemplateBatchMode.ZIP
                )
            )

        assertThat(response).isInstanceOf(UseCaseOutcome.Failure::class.java)
        val failure = response as UseCaseOutcome.Failure
        assertEquals(RenderTemplateBatchError.BATCH_REJECTED_ERROR, failure.errorCode)
        assertThat(failure.errorMessage).contains("Batch processing deactivated")
    }
}
