package com.aamdigital.aambackendservice.export.usecase

import com.aamdigital.aambackendservice.common.domain.DomainReference
import com.aamdigital.aambackendservice.common.domain.UseCaseOutcome
import com.aamdigital.aambackendservice.common.domain.UseCaseOutcome.Failure
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
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Default implementation of [RenderTemplateBatchUseCase].
 *
 * Handles two modes:
 *  - [RenderTemplateBatchMode.ZIP]: render each record independently and bundle the
 *    resulting files into a single ZIP. Existing single-record templates work without
 *    modification.
 *  - [RenderTemplateBatchMode.COMBINED]: forward the array as-is to the template engine
 *    and return its output (typically one multi-page file when the template uses array
 *    placeholders like `{d[i].field}`).
 */
class DefaultRenderTemplateBatchUseCase(
    private val renderClient: RestClient,
    private val objectMapper: ObjectMapper,
    private val templateStorage: TemplateStorage
) : RenderTemplateBatchUseCase() {
    private data class FileResponse(
        val file: ByteArray,
        val headers: HttpHeaders
    )

    override fun apply(request: RenderTemplateBatchRequest): UseCaseOutcome<RenderTemplateBatchData> {
        val dataArray =
            (request.bodyData.get("data") as? ArrayNode)
                ?: return Failure(
                    errorCode = RenderTemplateBatchError.INVALID_DATA_SHAPE_ERROR,
                    errorMessage = "Request body must contain a 'data' field of type array.",
                    cause = null
                )

        if (dataArray.isEmpty) {
            return Failure(
                errorCode = RenderTemplateBatchError.EMPTY_DATA_LIST_ERROR,
                errorMessage = "Request 'data' array must not be empty.",
                cause = null
            )
        }

        val template = fetchTemplate(request.templateRef)
        val targetFileName = template.targetFileName.replace(Regex("[\\\\/*?\"<>|]"), "_")

        return when (request.mode) {
            RenderTemplateBatchMode.ZIP -> renderAsZip(template, dataArray, targetFileName, request.bodyData)
            RenderTemplateBatchMode.COMBINED -> renderAsCombined(template, request.bodyData, targetFileName)
        }
    }

    private fun renderAsZip(
        template: TemplateExport,
        dataArray: ArrayNode,
        targetFileName: String,
        envelope: JsonNode
    ): UseCaseOutcome<RenderTemplateBatchData> {
        val zipBuffer = ByteArrayOutputStream()
        val failedIndices = mutableListOf<Int>()
        val zipHeaders = HttpHeaders()
        zipHeaders.contentType = MediaType.parseMediaType("application/zip")
        zipHeaders.set(
            HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"${sanitizedZipFileName(targetFileName)}\""
        )

        ZipOutputStream(zipBuffer).use { zip ->
            dataArray.forEachIndexed { index, record ->
                try {
                    val perRecordBody = perRecordEnvelope(envelope, record, targetFileName)
                    val pdf = renderSingleRecord(template.templateId, perRecordBody)
                    val entryName = chooseEntryName(pdf.headers, targetFileName, index)
                    zip.putNextEntry(ZipEntry(entryName))
                    zip.write(pdf.file)
                    zip.closeEntry()
                } catch (ex: Exception) {
                    logger.warn(
                        "[DefaultRenderTemplateBatchUseCase] Skipping record index $index after render failure",
                        ex
                    )
                    failedIndices.add(index)
                }
            }

            if (failedIndices.isNotEmpty()) {
                zip.putNextEntry(ZipEntry("failures.txt"))
                zip.write(
                    buildString {
                        appendLine("The following record indices failed to render and were not included:")
                        failedIndices.forEach { appendLine("- $it") }
                    }.toByteArray(Charsets.UTF_8)
                )
                zip.closeEntry()
            }
        }

        if (failedIndices.size == dataArray.size()) {
            return Failure(
                errorCode = RenderTemplateBatchError.ALL_RECORDS_FAILED_ERROR,
                errorMessage = "All ${dataArray.size()} records failed to render.",
                cause = null
            )
        }

        return Success(
            data = RenderTemplateBatchData(
                file = ByteArrayInputStream(zipBuffer.toByteArray()),
                responseHeaders = zipHeaders,
                failedIndices = failedIndices
            )
        )
    }

    private fun renderAsCombined(
        template: TemplateExport,
        bodyData: JsonNode,
        targetFileName: String
    ): UseCaseOutcome<RenderTemplateBatchData> {
        (bodyData as ObjectNode).put("reportName", targetFileName)
        val raw = createRenderRequest(template.templateId, bodyData)
        val renderId = parseRenderRequestResponse(raw)
        val pdf = fetchRenderIdRequest(renderId)
        return Success(
            data = RenderTemplateBatchData(
                file = ByteArrayInputStream(pdf.file),
                responseHeaders = pdf.headers,
                failedIndices = emptyList()
            )
        )
    }

    private fun perRecordEnvelope(
        envelope: JsonNode,
        record: JsonNode,
        targetFileName: String
    ): ObjectNode {
        val perRecord = objectMapper.createObjectNode()
        envelope.fields().forEach { (key, value) ->
            if (key != "data") perRecord.set<JsonNode>(key, value)
        }
        perRecord.set<JsonNode>("data", record)
        perRecord.put("reportName", targetFileName)
        return perRecord
    }

    private fun chooseEntryName(
        responseHeaders: HttpHeaders,
        targetFileName: String,
        index: Int
    ): String {
        val disposition = responseHeaders.getFirst(HttpHeaders.CONTENT_DISPOSITION)
        val fromHeader =
            disposition
                ?.let { Regex("""filename="?([^";]+)"?""").find(it)?.groupValues?.getOrNull(1) }
                ?.trim()
        return fromHeader?.takeIf { it.isNotBlank() }
            ?: "${targetFileName.substringBeforeLast('.', targetFileName)}-${index + 1}.pdf"
    }

    private fun sanitizedZipFileName(targetFileName: String): String {
        val base = targetFileName.substringBeforeLast('.', targetFileName)
        return "$base.zip"
    }

    private fun renderSingleRecord(
        templateId: String,
        body: JsonNode
    ): FileResponse {
        val raw = createRenderRequest(templateId, body)
        val renderId = parseRenderRequestResponse(raw)
        return fetchRenderIdRequest(renderId)
    }

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
}
