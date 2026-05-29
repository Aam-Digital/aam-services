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
import java.util.zip.CRC32
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

    private fun enqueueRenderAndZip(
        renderId: String,
        entries: List<String>
    ) {
        enqueueRenderAndZipBytes(
            renderId,
            zipBytes =
                ByteArrayOutputStream()
                    .also { zipBuffer ->
                        ZipOutputStream(zipBuffer).use { zip ->
                            entries.forEach { entryName ->
                                zip.putNextEntry(ZipEntry(entryName))
                                zip.write("content for $entryName".toByteArray())
                                zip.closeEntry()
                            }
                        }
                    }.toByteArray()
        )
    }

    private fun enqueueRenderAndZipBytes(
        renderId: String,
        zipBytes: ByteArray
    ) {
        mockWebServer.enqueue(
            MockResponse().setBody(
                """{"success":true,"data":{"renderId":"$renderId"}}"""
            )
        )
        val responseBuffer = Buffer().write(zipBytes)
        mockWebServer.enqueue(
            MockResponse()
                .setHeaders(
                    mapOf(
                        "Content-Type" to "application/zip",
                        "Content-Disposition" to "attachment; filename=\"$renderId.zip\"",
                        "Content-Length" to responseBuffer.size.toString()
                    ).toHeaders()
                ).setBody(responseBuffer)
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

    private fun storedZipWithDataDescriptors(entries: List<Pair<String, ByteArray>>): ByteArray {
        data class CentralDirectoryEntry(
            val name: String,
            val content: ByteArray,
            val crc32: Long,
            val localHeaderOffset: Int
        )

        val output = ByteArrayOutputStream()
        val centralDirectoryEntries = mutableListOf<CentralDirectoryEntry>()

        entries.forEach { (entryName, content) ->
            val nameBytes = entryName.toByteArray()
            val crc32 = CRC32().also { it.update(content) }.value
            val localHeaderOffset = output.size()

            output.writeIntLe(0x04034b50)
            output.writeShortLe(20)
            output.writeShortLe(0x0008)
            output.writeShortLe(0)
            output.writeShortLe(0)
            output.writeShortLe(0)
            output.writeIntLe(0)
            output.writeIntLe(0)
            output.writeIntLe(0)
            output.writeShortLe(nameBytes.size)
            output.writeShortLe(0)
            output.write(nameBytes)
            output.write(content)
            output.writeIntLe(0x08074b50)
            output.writeIntLe(crc32)
            output.writeIntLe(content.size.toLong())
            output.writeIntLe(content.size.toLong())

            centralDirectoryEntries.add(
                CentralDirectoryEntry(entryName, content, crc32, localHeaderOffset)
            )
        }

        val centralDirectoryOffset = output.size()
        centralDirectoryEntries.forEach { entry ->
            val nameBytes = entry.name.toByteArray()
            output.writeIntLe(0x02014b50)
            output.writeShortLe(20)
            output.writeShortLe(20)
            output.writeShortLe(0x0008)
            output.writeShortLe(0)
            output.writeShortLe(0)
            output.writeShortLe(0)
            output.writeIntLe(entry.crc32)
            output.writeIntLe(entry.content.size.toLong())
            output.writeIntLe(entry.content.size.toLong())
            output.writeShortLe(nameBytes.size)
            output.writeShortLe(0)
            output.writeShortLe(0)
            output.writeShortLe(0)
            output.writeShortLe(0)
            output.writeIntLe(0)
            output.writeIntLe(entry.localHeaderOffset.toLong())
            output.write(nameBytes)
        }

        val centralDirectorySize = output.size() - centralDirectoryOffset
        output.writeIntLe(0x06054b50)
        output.writeShortLe(0)
        output.writeShortLe(0)
        output.writeShortLe(entries.size)
        output.writeShortLe(entries.size)
        output.writeIntLe(centralDirectorySize.toLong())
        output.writeIntLe(centralDirectoryOffset.toLong())
        output.writeShortLe(0)

        return output.toByteArray()
    }

    private fun ByteArrayOutputStream.writeShortLe(value: Int) {
        write(value and 0xff)
        write((value ushr 8) and 0xff)
    }

    private fun ByteArrayOutputStream.writeIntLe(value: Long) {
        write((value ushr 0).toInt() and 0xff)
        write((value ushr 8).toInt() and 0xff)
        write((value ushr 16).toInt() and 0xff)
        write((value ushr 24).toInt() and 0xff)
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
    fun `ZIP mode should rewrite archive entries from target file name pattern`() {
        val templateRef = DomainReference("some-id")
        val bodyData: JsonNode =
            objectMapper.readValue(
                """
                {
                  "convertTo": "pdf",
                  "data": [
                    {"name": "Alice Smith", "center": {"name": "North"}},
                    {"name": "Bob/Builder", "center": {"name": "South"}}
                  ]
                }
                """.trimIndent()
            )
        whenever(templateStorage.fetchTemplate(templateRef))
            .thenReturn(template("test_letter_{d.name}_{d.center.name}.pdf"))
        enqueueRenderAndZip(
            renderId = "render-zip-1",
            entries =
                listOf(
                    "NkHyoRAYgddfsHJzWquB1QcmVwb3J0.pdf",
                    "hs_LeLHx09drWJ04KlERywcmVwb3J0.pdf"
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
            "test_letter_Alice Smith_North.pdf",
            "test_letter_Bob_Builder_South.pdf"
        )
    }

    @Test
    fun `ZIP mode should make static target file names readable and unique`() {
        val templateRef = DomainReference("some-id")
        val bodyData: JsonNode =
            objectMapper.readValue(
                """{"convertTo":"pdf","data":[{"name":"Alice"},{"name":"Bob"}]}"""
            )
        whenever(templateStorage.fetchTemplate(templateRef)).thenReturn(template("test_letter"))
        enqueueRenderAndZip(
            renderId = "render-zip-1",
            entries =
                listOf(
                    "NkHyoRAYgddfsHJzWquB1QcmVwb3J0.pdf",
                    "hs_LeLHx09drWJ04KlERywcmVwb3J0.pdf"
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
            "test_letter.pdf",
            "test_letter_2.pdf"
        )
    }

    @Test
    fun `ZIP mode should rewrite Carbone stored archive entries with data descriptors`() {
        val templateRef = DomainReference("some-id")
        val bodyData: JsonNode =
            objectMapper.readValue(
                """{"convertTo":"pdf","data":[{"name":"Alice"},{"name":"Bob"}]}"""
            )
        whenever(templateStorage.fetchTemplate(templateRef)).thenReturn(template("test_letter_{d.name}.pdf"))
        enqueueRenderAndZipBytes(
            renderId = "render-zip-1",
            zipBytes =
                storedZipWithDataDescriptors(
                    listOf(
                        "NkHyoRAYgddfsHJzWquB1QcmVwb3J0.pdf" to "alice".toByteArray(),
                        "hs_LeLHx09drWJ04KlERywcmVwb3J0.pdf" to "bob".toByteArray()
                    )
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
            "test_letter_Alice.pdf",
            "test_letter_Bob.pdf"
        )
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
