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
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

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

    private fun template(targetFileName: String = "child-report.pdf") =
        TemplateExport(
            id = "export-id",
            templateId = "export-template-id",
            targetFileName = targetFileName,
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

    /**
     * Mock a Carbone batch ZIP response. Carbone names each entry
     * `<22-random-chars><base64(realName)>.<ext>`; the supplied [decodedNames] are the
     * post-substitution readable names that should appear after the backend's decode step.
     */
    private fun enqueueRenderAndCarboneZip(
        renderId: String,
        decodedNames: List<String>
    ) {
        mockWebServer.enqueue(
            MockResponse().setBody(
                """{"success":true,"data":{"renderId":"$renderId"}}"""
            )
        )
        val zipBytes =
            ByteArrayOutputStream()
                .also { zipBuffer ->
                    ZipOutputStream(zipBuffer).use { zip ->
                        decodedNames.forEachIndexed { index, decoded ->
                            val randomPrefix = "abcdefghijklmnopqrstu".padEnd(22, ('a' + index))
                            val lastDot = decoded.lastIndexOf('.')
                            val ext = if (lastDot > 0) decoded.substring(lastDot) else ""
                            // Carbone base64-encodes the FULL filename (including its extension),
                            // then appends the extension a second time outside the encoded segment.
                            val encoded =
                                Base64
                                    .getEncoder()
                                    .withoutPadding()
                                    .encodeToString(decoded.toByteArray())
                            zip.putNextEntry(ZipEntry("$randomPrefix$encoded$ext"))
                            zip.write("content $index".toByteArray())
                            zip.closeEntry()
                        }
                    }
                }.toByteArray()
        val buffer = Buffer().write(zipBytes)
        mockWebServer.enqueue(
            MockResponse()
                .setHeaders(
                    mapOf(
                        "Content-Type" to "application/zip",
                        "Content-Disposition" to "attachment; filename=\"$renderId\"",
                        "Content-Length" to buffer.size.toString()
                    ).toHeaders()
                ).setBody(buffer)
        )
    }

    private fun zipEntryNames(file: InputStream): List<String> {
        val names = mutableListOf<String>()
        ZipInputStream(file).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                names.add(entry.name)
                entry = zip.nextEntry
            }
        }
        return names
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
    fun `ZIP mode should forward batchSplitBy and batchReportName with the targetFileName pattern`() {
        val templateRef = DomainReference("some-id")
        val bodyData: JsonNode =
            objectMapper.readValue(
                """{"convertTo":"pdf","data":[{"name":"Alice"},{"name":"Bob"}]}"""
            )
        whenever(templateStorage.fetchTemplate(templateRef))
            .thenReturn(template("test_letter_{d.name}.pdf"))
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

        val renderRequest = mockWebServer.takeRequest()
        assertThat(renderRequest.path).isEqualTo("/render/export-template-id")
        val sentBody: Map<String, Any?> = objectMapper.readValue(renderRequest.body.readUtf8())
        assertThat(sentBody["batchSplitBy"]).isEqualTo("d")
        assertThat(sentBody["batchOutput"]).isEqualTo("zip")
        assertThat(sentBody["reportName"]).isEqualTo("test_letter_{d.name}.pdf")
        assertThat(sentBody["batchReportName"]).isEqualTo("test_letter_{d.name}.pdf")
        assertThat(sentBody["data"]).isInstanceOf(List::class.java)
    }

    @Test
    fun `ZIP mode should sanitize illegal filesystem characters from the target file name pattern`() {
        val templateRef = DomainReference("some-id")
        val bodyData: JsonNode =
            objectMapper.readValue("""{"data":[{"name":"X"}]}""")
        whenever(templateStorage.fetchTemplate(templateRef))
            .thenReturn(template("""bad/path*"<>|name_{d.name}.pdf"""))
        enqueueRenderAndFile(renderId = "render-zip-1", contentType = "application/zip")

        service.run(
            RenderTemplateBatchRequest(
                templateRef = templateRef,
                bodyData = bodyData,
                mode = RenderTemplateBatchMode.ZIP
            )
        )

        val renderRequest = mockWebServer.takeRequest()
        val sentBody: Map<String, Any?> = objectMapper.readValue(renderRequest.body.readUtf8())
        assertThat(sentBody["batchReportName"]).isEqualTo("bad_path_____name_{d.name}.pdf")
    }

    @Test
    fun `ZIP mode should append pdf extension when target file name has none`() {
        val templateRef = DomainReference("some-id")
        val bodyData: JsonNode =
            objectMapper.readValue("""{"data":[{"name":"X"}]}""")
        whenever(templateStorage.fetchTemplate(templateRef)).thenReturn(template("monthly_report_{d.name}"))
        enqueueRenderAndFile(renderId = "render-zip-1", contentType = "application/zip")

        service.run(
            RenderTemplateBatchRequest(
                templateRef = templateRef,
                bodyData = bodyData,
                mode = RenderTemplateBatchMode.ZIP
            )
        )

        val renderRequest = mockWebServer.takeRequest()
        val sentBody: Map<String, Any?> = objectMapper.readValue(renderRequest.body.readUtf8())
        assertThat(sentBody["reportName"]).isEqualTo("monthly_report_{d.name}.pdf")
        assertThat(sentBody["batchReportName"]).isEqualTo("monthly_report_{d.name}.pdf")
    }

    @Test
    fun `ZIP mode should decode Carbone's encoded entry names into readable filenames`() {
        val templateRef = DomainReference("some-id")
        val bodyData: JsonNode =
            objectMapper.readValue(
                """{"convertTo":"pdf","data":[{"name":"Anna"},{"name":"Ben"}]}"""
            )
        whenever(templateStorage.fetchTemplate(templateRef))
            .thenReturn(template("Child Report - {d.name}.pdf"))
        enqueueRenderAndCarboneZip(
            renderId = "render-zip-1",
            decodedNames =
                listOf(
                    "Child Report - Anna.pdf",
                    "Child Report - Ben.pdf"
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

        assertThat(response).isInstanceOf(UseCaseOutcome.Success::class.java)
        val data = (response as UseCaseOutcome.Success).data
        assertThat(zipEntryNames(data.file)).containsExactly(
            "Child Report - Anna.pdf",
            "Child Report - Ben.pdf"
        )
    }

    @Test
    fun `ZIP mode should disambiguate duplicate decoded entry names with a numeric suffix`() {
        val templateRef = DomainReference("some-id")
        val bodyData: JsonNode =
            objectMapper.readValue(
                """{"convertTo":"pdf","data":[{"name":"Anna"},{"name":"Anna"}]}"""
            )
        whenever(templateStorage.fetchTemplate(templateRef))
            .thenReturn(template("Child Report - {d.name}.pdf"))
        enqueueRenderAndCarboneZip(
            renderId = "render-zip-1",
            decodedNames =
                listOf(
                    "Child Report - Anna.pdf",
                    "Child Report - Anna.pdf"
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

        assertThat(response).isInstanceOf(UseCaseOutcome.Success::class.java)
        val data = (response as UseCaseOutcome.Success).data
        assertThat(zipEntryNames(data.file)).containsExactly(
            "Child Report - Anna.pdf",
            "Child Report - Anna_2.pdf"
        )
    }

    @Test
    fun `COMBINED mode should forward batchOutput pdf and omit batchReportName`() {
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
        assertThat(sentBody).doesNotContainKey("batchReportName")
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
