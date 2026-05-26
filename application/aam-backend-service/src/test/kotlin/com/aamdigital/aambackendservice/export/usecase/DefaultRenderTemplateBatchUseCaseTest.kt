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
import java.util.zip.ZipInputStream

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

    private fun enqueueSuccessfulRender(renderId: String) {
        mockWebServer.enqueue(
            MockResponse().setBody(
                """
                {
                    "success": true,
                    "data": {
                        "renderId": "$renderId"
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
                        "Content-Disposition" to "attachment; filename=\"$renderId.pdf\"",
                        "Content-Length" to buffer.size.toString()
                    ).toHeaders()
                ).setBody(buffer)
        )
    }

    private fun enqueueRenderFailure() {
        mockWebServer.enqueue(
            MockResponse().setBody(
                """
                {
                    "success": false,
                    "error": "boom"
                }
                """.trimIndent()
            )
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
    fun `ZIP mode should return a ZIP containing one entry per successful record`() {
        val templateRef = DomainReference("some-id")
        val bodyData: JsonNode =
            objectMapper.readValue(
                """{"convertTo":"pdf","data":[{"name":"Alice"},{"name":"Bob"}]}"""
            )

        whenever(templateStorage.fetchTemplate(templateRef)).thenReturn(template())
        enqueueSuccessfulRender("render-1")
        enqueueSuccessfulRender("render-2")

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
        assertThat(data.failedIndices).isEmpty()
        assertThat(data.responseHeaders.contentType?.toString()).isEqualTo("application/zip")

        val zipEntries = mutableListOf<String>()
        ZipInputStream(data.file).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                zipEntries.add(entry.name)
                entry = zis.nextEntry
            }
        }
        assertThat(zipEntries).hasSize(2)
        assertThat(zipEntries).contains("render-1.pdf", "render-2.pdf")
    }

    @Test
    fun `ZIP mode should skip failed records and report them in failedIndices plus a failures-txt entry`() {
        val templateRef = DomainReference("some-id")
        val bodyData: JsonNode =
            objectMapper.readValue(
                """{"data":[{"name":"Alice"},{"name":"Broken"},{"name":"Carla"}]}"""
            )

        whenever(templateStorage.fetchTemplate(templateRef)).thenReturn(template())
        enqueueSuccessfulRender("render-A")
        enqueueRenderFailure()
        enqueueSuccessfulRender("render-C")

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
        assertThat(data.failedIndices).containsExactly(1)

        val zipEntries = mutableListOf<String>()
        ZipInputStream(data.file).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                zipEntries.add(entry.name)
                entry = zis.nextEntry
            }
        }
        assertThat(zipEntries).hasSize(3)
        assertThat(zipEntries).contains("render-A.pdf", "render-C.pdf", "failures.txt")
    }

    @Test
    fun `ZIP mode should return ALL_RECORDS_FAILED_ERROR when every record fails`() {
        val templateRef = DomainReference("some-id")
        val bodyData: JsonNode =
            objectMapper.readValue("""{"data":[{"name":"X"},{"name":"Y"}]}""")

        whenever(templateStorage.fetchTemplate(templateRef)).thenReturn(template())
        enqueueRenderFailure()
        enqueueRenderFailure()

        val response =
            service.run(
                RenderTemplateBatchRequest(
                    templateRef = templateRef,
                    bodyData = bodyData,
                    mode = RenderTemplateBatchMode.ZIP
                )
            )

        assertThat(response).isInstanceOf(UseCaseOutcome.Failure::class.java)
        assertEquals(
            RenderTemplateBatchError.ALL_RECORDS_FAILED_ERROR,
            (response as UseCaseOutcome.Failure).errorCode
        )
    }

    @Test
    fun `COMBINED mode should forward the array as-is and return Carbone's single file response`() {
        val templateRef = DomainReference("some-id")
        val bodyData: JsonNode =
            objectMapper.readValue("""{"data":[{"name":"Alice"},{"name":"Bob"}]}""")

        whenever(templateStorage.fetchTemplate(templateRef)).thenReturn(template())
        enqueueSuccessfulRender("combined-render")

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
        assertThat(data.failedIndices).isEmpty()
        assertThat(data.responseHeaders.contentType?.toString()).isEqualTo("application/pdf")
    }
}
