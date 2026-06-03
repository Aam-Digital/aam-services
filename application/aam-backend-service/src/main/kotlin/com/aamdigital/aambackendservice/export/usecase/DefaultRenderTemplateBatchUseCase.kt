package com.aamdigital.aambackendservice.export.usecase

import com.aamdigital.aambackendservice.common.domain.DomainReference
import com.aamdigital.aambackendservice.common.domain.UseCaseOutcome
import com.aamdigital.aambackendservice.common.domain.UseCaseOutcome.Success
import com.aamdigital.aambackendservice.common.error.ExternalSystemException
import com.aamdigital.aambackendservice.common.error.NetworkException
import com.aamdigital.aambackendservice.common.error.NotFoundException
import com.aamdigital.aambackendservice.export.core.RenderTemplateBatchData
import com.aamdigital.aambackendservice.export.core.RenderTemplateBatchError
import com.aamdigital.aambackendservice.export.core.RenderTemplateBatchMode
import com.aamdigital.aambackendservice.export.core.RenderTemplateBatchRequest
import com.aamdigital.aambackendservice.export.core.RenderTemplateBatchUseCase
import com.aamdigital.aambackendservice.export.core.TemplateExport
import com.aamdigital.aambackendservice.export.core.TemplateStorage
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.client.ResourceAccessException
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
    private val renderClient: RestClient,
    private val objectMapper: ObjectMapper,
    private val templateStorage: TemplateStorage
) : RenderTemplateBatchUseCase() {
    override fun apply(request: RenderTemplateBatchRequest): UseCaseOutcome<RenderTemplateBatchData> {
        val bodyData = request.bodyData
        if (bodyData !is ObjectNode) {
            return UseCaseOutcome.Failure(
                errorCode = RenderTemplateBatchError.INVALID_DATA_SHAPE_ERROR,
                errorMessage = "Request body must be a JSON object.",
                cause = null
            )
        }
        val dataArray =
            bodyData.get("data") as? ArrayNode
                ?: return UseCaseOutcome.Failure(
                    errorCode = RenderTemplateBatchError.INVALID_DATA_SHAPE_ERROR,
                    errorMessage = "Request body must contain a 'data' field of type array.",
                    cause = null
                )
        if (dataArray.isEmpty) {
            return UseCaseOutcome.Failure(
                errorCode = RenderTemplateBatchError.EMPTY_DATA_LIST_ERROR,
                errorMessage = "Request 'data' array must not be empty.",
                cause = null
            )
        }

        val template = fetchTemplate(request.templateRef)
        val targetFileName = template.targetFileName.replace(Regex("[\\\\/*?\"<>|]"), "_")

        bodyData.put("batchSplitBy", "d")
        bodyData.put("batchOutput", carboneBatchOutput(request.mode))
        bodyData.put("reportName", targetFileName)
        if (request.mode == RenderTemplateBatchMode.ZIP) {
            // Carbone v5+ substitutes {d.field} placeholders per record for each zip entry name
            bodyData.put("batchReportName", targetFileName)
        }

        val raw = createRenderRequest(template.templateId, bodyData)
        val renderId = parseRenderRequestResponse(raw)
        val file = fetchRenderIdRequest(renderId)
        val fileBytes =
            if (request.mode == RenderTemplateBatchMode.ZIP) {
                decodeZipEntryNames(file.file)
            } else {
                file.file
            }

        return Success(
            data =
                RenderTemplateBatchData(
                    file = ByteArrayInputStream(fileBytes),
                    responseHeaders = file.headers
                )
        )
    }

    private fun carboneBatchOutput(mode: RenderTemplateBatchMode): String =
        when (mode) {
            RenderTemplateBatchMode.ZIP -> "zip"
            RenderTemplateBatchMode.COMBINED -> "pdf"
        }

    private data class FileResponse(
        val file: ByteArray,
        val headers: HttpHeaders
    )

    private data class ZipEntryData(
        val name: String,
        val content: ByteArray
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
                    ex
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
                            content = zip.getInputStream(entry).use { it.readBytes() }
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
        usedNames: MutableSet<String>
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

    private fun fetchTemplate(templateRef: DomainReference): TemplateExport =
        try {
            templateStorage.fetchTemplate(templateRef)
        } catch (ex: Exception) {
            throw when (ex) {
                is NotFoundException -> {
                    NotFoundException(
                        cause = ex.cause ?: ex,
                        message = ex.localizedMessage,
                        code = RenderTemplateBatchError.NOT_FOUND_ERROR
                    )
                }

                is NetworkException -> {
                    NetworkException(
                        cause = ex.cause ?: ex,
                        message = ex.localizedMessage,
                        code = RenderTemplateBatchError.FETCH_TEMPLATE_FAILED_ERROR
                    )
                }

                else -> {
                    ExternalSystemException(
                        cause = ex.cause ?: ex,
                        message = "Could not fetch template metadata.",
                        code = RenderTemplateBatchError.FETCH_TEMPLATE_FAILED_ERROR
                    )
                }
            }
        }

    private fun createRenderRequest(
        templateId: String,
        bodyData: JsonNode
    ): String {
        val response =
            try {
                renderClient
                    .post()
                    .uri("/render/$templateId")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(bodyData)
                    .retrieve()
                    .body(String::class.java)
            } catch (ex: Exception) {
                // Carbone returns HTTP 5xx with a structured JSON body for batch rejections
                // (e.g. exceeding nbReportMaxPerBatch). Try to surface that error message.
                val carboneMessage = extractCarboneErrorMessage(ex)
                if (carboneMessage != null) {
                    throw ExternalSystemException(
                        cause = ex,
                        message = carboneMessage,
                        code = RenderTemplateBatchError.BATCH_REJECTED_ERROR
                    )
                }
                throw ExternalSystemException(
                    cause = ex,
                    message = ex.localizedMessage,
                    code = RenderTemplateBatchError.CREATE_RENDER_REQUEST_FAILED_ERROR
                )
            }

        if (response.isNullOrEmpty()) {
            throw ExternalSystemException(
                cause = null,
                message = "Null or empty response from renderClient.",
                code = RenderTemplateBatchError.CREATE_RENDER_REQUEST_FAILED_ERROR
            )
        }

        return response
    }

    /**
     * Pull the structured `error` message out of a Carbone HTTP error response if present.
     * Expected body shape: `{"success":false,"error":"...","code":"w101","data":{"renderId":""}}`.
     *
     * The exception thrown by [RestClient] for HTTP 5xx may be the
     * [org.springframework.web.client.RestClientResponseException] directly or a wrapping
     * cause, so we walk the cause chain.
     */
    private fun extractCarboneErrorMessage(ex: Throwable): String? {
        var current: Throwable? = ex
        var body: String? = null
        while (current != null) {
            if (current is org.springframework.web.client.RestClientResponseException) {
                body = current.responseBodyAsString
                break
            }
            current = current.cause
        }
        if (body.isNullOrBlank()) return null
        return try {
            val parsed = objectMapper.readValue(body, RenderRequestErrorResponseDto::class.java)
            parsed.error.takeIf { it.isNotBlank() }
        } catch (ignored: Exception) {
            null
        }
    }

    private fun fetchRenderIdRequest(renderId: String): FileResponse =
        try {
            renderClient
                .get()
                .uri("/render/$renderId")
                .accept(MediaType.APPLICATION_JSON)
                .exchange { _, clientResponse ->
                    val responseHeaders = clientResponse.headers

                    val forwardHeaders = HttpHeaders()
                    forwardHeaders.contentType = responseHeaders.contentType

                    if (!responseHeaders[HttpHeaders.CONTENT_DISPOSITION].isNullOrEmpty()) {
                        forwardHeaders[HttpHeaders.CONTENT_DISPOSITION] =
                            responseHeaders[HttpHeaders.CONTENT_DISPOSITION]
                    }

                    val buffer =
                        clientResponse.bodyTo(ByteArray::class.java) ?: throw ExternalSystemException(
                            cause = null,
                            message = "Could not read body bytes from template engine response.",
                            code = RenderTemplateBatchError.FETCH_RENDER_ID_REQUEST_FAILED_ERROR
                        )

                    FileResponse(
                        file = buffer,
                        headers = forwardHeaders
                    )
                } ?: throw ExternalSystemException(
                cause = null,
                message = "Could not fetch render response from template engine.",
                code = RenderTemplateBatchError.FETCH_RENDER_ID_REQUEST_FAILED_ERROR
            )
        } catch (ex: Exception) {
            throw when (ex) {
                is ResourceAccessException -> {
                    NetworkException(
                        cause = ex.cause ?: ex,
                        message = ex.localizedMessage,
                        code = RenderTemplateBatchError.FETCH_RENDER_ID_REQUEST_FAILED_ERROR
                    )
                }

                else -> {
                    ExternalSystemException(
                        cause = ex.cause ?: ex,
                        message = "Could not fetch render result from template engine.",
                        code = RenderTemplateBatchError.FETCH_RENDER_ID_REQUEST_FAILED_ERROR
                    )
                }
            }
        }

    private fun parseRenderRequestResponse(raw: String): String {
        try {
            val parsed = objectMapper.readValue(raw, RenderRequestResponseDto::class.java)
            return parsed.data.renderId
        } catch (ex: Exception) {
            val errorMessage =
                try {
                    val errorResponse = objectMapper.readValue(raw, RenderRequestErrorResponseDto::class.java)
                    errorResponse.error
                } catch (ignored: Exception) {
                    ex.localizedMessage
                }

            throw ExternalSystemException(
                cause = ex,
                message = errorMessage,
                code = RenderTemplateBatchError.PARSE_RESPONSE_ERROR
            )
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DefaultRenderTemplateBatchUseCase::class.java)

        /** Length of the random prefix Carbone prepends to each ZIP entry filename (the SDK's `renderPrefix` slot). */
        private const val CARBONE_RANDOM_PREFIX_LEN = 22
    }
}
