package com.aamdigital.aambackendservice.export.usecase

import com.aamdigital.aambackendservice.common.domain.UseCaseOutcome
import com.aamdigital.aambackendservice.common.domain.UseCaseOutcome.Success
import com.aamdigital.aambackendservice.export.core.RenderTemplateBatchData
import com.aamdigital.aambackendservice.export.core.RenderTemplateBatchError
import com.aamdigital.aambackendservice.export.core.RenderTemplateBatchMode
import com.aamdigital.aambackendservice.export.core.RenderTemplateBatchRequest
import com.aamdigital.aambackendservice.export.core.RenderTemplateBatchUseCase
import com.aamdigital.aambackendservice.export.core.TemplateStorage
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import org.slf4j.LoggerFactory
import org.springframework.web.client.RestClient
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Default implementation of [RenderTemplateBatchUseCase].
 *
 * Forwards the request to Carbone's native batch rendering (`batchSplitBy` + `batchOutput`).
 * Carbone splits the `data` array, renders each record with the existing single-record template,
 * and aggregates the result into a single response:
 *
 *  - [RenderTemplateBatchMode.ZIP]      -> `batchOutput: "zip"`  -> ZIP of N PDFs
 *  - [RenderTemplateBatchMode.COMBINED] -> `batchOutput: "pdf"`  -> one merged multi-page PDF
 *
 * The Carbone instance must have `nbReportMaxPerBatch > 0` in its config to enable batch.
 * If the array exceeds the configured limit, Carbone returns an error message which is propagated
 * verbatim to the caller as [RenderTemplateBatchError.BATCH_REJECTED_ERROR].
 */
class DefaultRenderTemplateBatchUseCase(
    renderClient: RestClient,
    objectMapper: ObjectMapper,
    templateStorage: TemplateStorage,
) : RenderTemplateBatchUseCase() {
    private val carboneClient =
        CarboneRenderApiClient(
            renderClient = renderClient,
            objectMapper = objectMapper,
            templateStorage = templateStorage,
            notFoundCode = RenderTemplateBatchError.NOT_FOUND_ERROR,
            fetchTemplateFailedCode = RenderTemplateBatchError.FETCH_TEMPLATE_FAILED_ERROR,
            createRenderRequestFailedCode = RenderTemplateBatchError.CREATE_RENDER_REQUEST_FAILED_ERROR,
            batchRejectedCode = RenderTemplateBatchError.BATCH_REJECTED_ERROR,
            fetchRenderResultFailedCode = RenderTemplateBatchError.FETCH_RENDER_ID_REQUEST_FAILED_ERROR,
            parseResponseCode = RenderTemplateBatchError.PARSE_RESPONSE_ERROR,
        )

    override fun apply(request: RenderTemplateBatchRequest): UseCaseOutcome<RenderTemplateBatchData> {
        val bodyData = request.bodyData
        if (bodyData !is ObjectNode) {
            return UseCaseOutcome.Failure(
                errorCode = RenderTemplateBatchError.INVALID_DATA_SHAPE_ERROR,
                errorMessage = "Request body must be a JSON object.",
                cause = null,
            )
        }
        val dataArray =
            bodyData.get("data") as? ArrayNode
                ?: return UseCaseOutcome.Failure(
                    errorCode = RenderTemplateBatchError.INVALID_DATA_SHAPE_ERROR,
                    errorMessage = "Request body must contain a 'data' field of type array.",
                    cause = null,
                )
        if (dataArray.isEmpty) {
            return UseCaseOutcome.Failure(
                errorCode = RenderTemplateBatchError.EMPTY_DATA_LIST_ERROR,
                errorMessage = "Request 'data' array must not be empty.",
                cause = null,
            )
        }

        val template = carboneClient.fetchTemplate(request.templateRef)
        val sanitizedTargetFileName = template.targetFileName.replace(Regex("[\\\\/*?\"<>|]"), "_")
        val targetFileName =
            if (sanitizedTargetFileName.endsWith(".pdf", ignoreCase = true)) {
                sanitizedTargetFileName
            } else {
                "$sanitizedTargetFileName.pdf"
            }

        bodyData.put("batchSplitBy", "d")
        bodyData.put("batchOutput", carboneBatchOutput(request.mode))
        bodyData.put("reportName", targetFileName)
        if (request.mode == RenderTemplateBatchMode.ZIP) {
            // Carbone v5+ substitutes {d.field} placeholders per record for each zip entry name
            bodyData.put("batchReportName", targetFileName)
        }

        val raw = carboneClient.createRenderRequest(template.templateId, bodyData)
        val renderId = carboneClient.parseRenderId(raw)
        val result = carboneClient.fetchRenderResult(renderId)
        val fileBytes =
            if (request.mode == RenderTemplateBatchMode.ZIP) {
                decodeZipEntryNames(result.file)
            } else {
                result.file
            }

        return Success(
            data =
                RenderTemplateBatchData(
                    file = ByteArrayInputStream(fileBytes),
                    responseHeaders = result.headers,
                ),
        )
    }

    private fun carboneBatchOutput(mode: RenderTemplateBatchMode): String =
        when (mode) {
            RenderTemplateBatchMode.ZIP -> "zip"
            RenderTemplateBatchMode.COMBINED -> "pdf"
        }

    private data class ZipEntryData(
        val name: String,
        val content: ByteArray,
    )

    /**
     * Carbone returns each ZIP entry as `<22-random-chars><base64-encoded-name>.<ext>` (a stable POSIX-safe
     * format used by `decodeOutputFilename()` in the SDK). The base64 part already reflects the placeholder
     * substitution we asked for via `batchReportName`. We just decode it so the user sees a readable filename.
     */
    private fun decodeZipEntryNames(zipBytes: ByteArray): ByteArray {
        val entries =
            try {
                readZipEntries(zipBytes)
            } catch (ex: Exception) {
                logger.warn(
                    "Could not parse Carbone batch ZIP; returning archive with engine-generated entry names.",
                    ex,
                )
                return zipBytes
            }
        if (entries.isEmpty()) return zipBytes

        val usedNames = mutableSetOf<String>()
        val rewritten = ByteArrayOutputStream()
        ZipOutputStream(rewritten).use { zip ->
            entries.forEach { entry ->
                val decoded = decodeCarboneEntryName(entry.name)
                zip.putNextEntry(ZipEntry(uniqueFileName(decoded, usedNames)))
                zip.write(entry.content)
                zip.closeEntry()
            }
        }
        return rewritten.toByteArray()
    }

    /**
     * Strip Carbone's 22-character random prefix and base64-decode the remaining filename.
     *
     * Carbone's entry name shape is `<22-random-chars><base64(originalFileName)>.<extension>`,
     * where the base64-encoded segment already includes the extension and the trailing
     * `.<extension>` after it is a duplicate that makes the path POSIX-safe on disk.
     *
     * Returns the original entry name unchanged if the input does not match this shape.
     */
    private fun decodeCarboneEntryName(entryName: String): String {
        val lastDot = entryName.lastIndexOf('.')
        val base = if (lastDot > 0) entryName.substring(0, lastDot) else entryName
        if (base.length <= CARBONE_RANDOM_PREFIX_LEN) return entryName
        val encoded = base.substring(CARBONE_RANDOM_PREFIX_LEN)
        // Carbone strips the trailing `=` padding from the base64 segment; restore it before decoding.
        val padded = encoded.padEnd(encoded.length + (4 - encoded.length % 4) % 4, '=')
        return try {
            val decoded = String(Base64.getDecoder().decode(padded), Charsets.UTF_8)
            decoded.takeIf { it.isNotBlank() } ?: entryName
        } catch (ignored: IllegalArgumentException) {
            // entry name was not in Carbone's encoded format
            entryName
        }
    }

    private fun readZipEntries(zipBytes: ByteArray): List<ZipEntryData> {
        val tempZip = Files.createTempFile("render-batch-", ".zip")
        return try {
            Files.write(tempZip, zipBytes)
            ZipFile(tempZip.toFile()).use { zip ->
                zip
                    .entries()
                    .asSequence()
                    .filter { !it.isDirectory }
                    .map { entry ->
                        ZipEntryData(
                            name = entry.name,
                            content = zip.getInputStream(entry).use { it.readBytes() },
                        )
                    }.toList()
            }
        } finally {
            Files.deleteIfExists(tempZip)
        }
    }

    private fun <T> java.util.Enumeration<T>.asSequence(): Sequence<T> =
        sequence {
            while (hasMoreElements()) {
                yield(nextElement())
            }
        }

    private fun uniqueFileName(
        fileName: String,
        usedNames: MutableSet<String>,
    ): String {
        var candidate = fileName
        val extensionStart = fileName.lastIndexOf('.')
        val extension = if (extensionStart > 0) fileName.substring(extensionStart) else ""
        val baseName = if (extension.isBlank()) fileName else fileName.dropLast(extension.length)
        var suffix = 2
        while (!usedNames.add(candidate)) {
            candidate = "${baseName}_$suffix$extension"
            suffix++
        }
        return candidate
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DefaultRenderTemplateBatchUseCase::class.java)

        /** Length of the random prefix Carbone prepends to each ZIP entry filename (the SDK's `renderPrefix` slot). */
        private const val CARBONE_RANDOM_PREFIX_LEN = 22
    }
}
