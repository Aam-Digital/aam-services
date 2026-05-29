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
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
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

        val raw = createRenderRequest(template.templateId, bodyData)
        val renderId = parseRenderRequestResponse(raw)
        val file = fetchRenderIdRequest(renderId)
        val fileBytes =
            if (request.mode == RenderTemplateBatchMode.ZIP) {
                rewriteZipEntries(file.file, dataArray, targetFileName)
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

    private fun fetchTemplate(templateRef: DomainReference): TemplateExport =
        try {
            templateStorage.fetchTemplate(templateRef)
        } catch (ex: Exception) {
            throw when (ex) {
                is NotFoundException ->
                    NotFoundException(
                        cause = ex.cause ?: ex,
                        message = ex.localizedMessage,
                        code = RenderTemplateBatchError.NOT_FOUND_ERROR
                    )

                is NetworkException ->
                    NetworkException(
                        cause = ex.cause ?: ex,
                        message = ex.localizedMessage,
                        code = RenderTemplateBatchError.FETCH_TEMPLATE_FAILED_ERROR
                    )

                else ->
                    ExternalSystemException(
                        cause = ex.cause ?: ex,
                        message = "Could not fetch template metadata.",
                        code = RenderTemplateBatchError.FETCH_TEMPLATE_FAILED_ERROR
                    )
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

                    if (!responseHeaders["Content-Disposition"].isNullOrEmpty()) {
                        forwardHeaders["Content-Disposition"] = responseHeaders["Content-Disposition"]
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
                is ResourceAccessException ->
                    NetworkException(
                        cause = ex.cause ?: ex,
                        message = ex.localizedMessage,
                        code = RenderTemplateBatchError.FETCH_RENDER_ID_REQUEST_FAILED_ERROR
                    )

                else ->
                    ExternalSystemException(
                        cause = ex.cause ?: ex,
                        message = "Could not fetch render result from template engine.",
                        code = RenderTemplateBatchError.FETCH_RENDER_ID_REQUEST_FAILED_ERROR
                    )
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

    private fun rewriteZipEntries(
        zipBytes: ByteArray,
        dataArray: ArrayNode,
        targetFileNamePattern: String
    ): ByteArray {
        val entries =
            try {
                readZipEntries(zipBytes)
            } catch (ex: Exception) {
                return zipBytes
            }
        if (entries.isEmpty()) return zipBytes

        val usedNames = mutableSetOf<String>()
        val rewrittenZip = ByteArrayOutputStream()
        ZipOutputStream(rewrittenZip).use { zip ->
            entries.forEachIndexed { index, entry ->
                val record = dataArray.get(index)
                val resolvedName =
                    if (record == null) {
                        sanitizeFileName(entry.name)
                    } else {
                        renderTargetFileName(targetFileNamePattern, record, entry.name, index)
                    }
                zip.putNextEntry(ZipEntry(uniqueFileName(resolvedName, usedNames)))
                zip.write(entry.content)
                zip.closeEntry()
            }
        }
        return rewrittenZip.toByteArray()
    }

    private fun readZipEntries(zipBytes: ByteArray): List<ZipEntryData> {
        val tempZip = Files.createTempFile("render-batch-", ".zip")
        return try {
            Files.write(tempZip, zipBytes)
            ZipFile(tempZip.toFile()).use { zip ->
                zip.entries().asSequence().filter { !it.isDirectory }.map { entry ->
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

    private fun renderTargetFileName(
        targetFileNamePattern: String,
        record: JsonNode,
        originalEntryName: String,
        index: Int
    ): String {
        val resolvedName =
            CARBONE_DATA_PLACEHOLDER.replace(targetFileNamePattern) { match ->
                resolveDataPath(record, match.groupValues[1]) ?: ""
            }
        val originalExtension = fileExtension(originalEntryName)
        val withExtension =
            if (fileExtension(resolvedName).isBlank() && originalExtension.isNotBlank()) {
                resolvedName + originalExtension
            } else {
                resolvedName
            }
        val sanitizedName = sanitizeFileName(withExtension)
        return sanitizedName.takeUnless { it.isBlank() || it == originalExtension }
            ?: "record-${index + 1}$originalExtension"
    }

    private fun resolveDataPath(
        record: JsonNode,
        path: String
    ): String? {
        var current: JsonNode = record
        path.split(".").forEach { segment ->
            current = current.get(segment) ?: return null
        }
        if (current.isNull || current.isMissingNode) return null
        return if (current.isValueNode) current.asText() else current.toString()
    }

    private fun sanitizeFileName(fileName: String): String =
        fileName
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .trim()

    private fun uniqueFileName(
        fileName: String,
        usedNames: MutableSet<String>
    ): String {
        var candidate = fileName
        val extension = fileExtension(fileName)
        val baseName = if (extension.isBlank()) fileName else fileName.dropLast(extension.length)
        var suffix = 2
        while (!usedNames.add(candidate)) {
            candidate = "${baseName}_$suffix$extension"
            suffix++
        }
        return candidate
    }

    private fun fileExtension(fileName: String): String {
        val nameOnly = fileName.substringAfterLast("/").substringAfterLast("\\")
        val extensionStart = nameOnly.lastIndexOf(".")
        val extension = if (extensionStart > 0) nameOnly.substring(extensionStart) else ""
        return extension.takeIf {
            it.length in 2..10 && it.drop(1).all { char -> char.isLetterOrDigit() }
        } ?: ""
    }

    companion object {
        private val CARBONE_DATA_PLACEHOLDER = Regex("\\{d\\.([^}:]+)(?::[^}]*)?}")
    }
}
