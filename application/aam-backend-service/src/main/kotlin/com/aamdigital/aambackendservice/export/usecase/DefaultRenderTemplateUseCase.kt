package com.aamdigital.aambackendservice.export.usecase

import com.aamdigital.aambackendservice.common.domain.UseCaseOutcome
import com.aamdigital.aambackendservice.common.domain.UseCaseOutcome.Success
import com.aamdigital.aambackendservice.export.core.RenderTemplateData
import com.aamdigital.aambackendservice.export.core.RenderTemplateError
import com.aamdigital.aambackendservice.export.core.RenderTemplateRequest
import com.aamdigital.aambackendservice.export.core.RenderTemplateUseCase
import com.aamdigital.aambackendservice.export.core.TemplateStorage
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.springframework.web.client.RestClient

class DefaultRenderTemplateUseCase(
    renderClient: RestClient,
    objectMapper: ObjectMapper,
    templateStorage: TemplateStorage,
) : RenderTemplateUseCase() {
    private val carboneClient =
        CarboneRenderApiClient(
            renderClient = renderClient,
            objectMapper = objectMapper,
            templateStorage = templateStorage,
            notFoundCode = RenderTemplateError.NOT_FOUND_ERROR,
            fetchTemplateFailedCode = RenderTemplateError.FETCH_TEMPLATE_FAILED_ERROR,
            createRenderRequestFailedCode = RenderTemplateError.CREATE_RENDER_REQUEST_FAILED_ERROR,
            fetchRenderResultFailedCode = RenderTemplateError.FETCH_RENDER_ID_REQUEST_FAILED_ERROR,
            parseResponseCode = RenderTemplateError.PARSE_RESPONSE_ERROR,
        )

    override fun apply(request: RenderTemplateRequest): UseCaseOutcome<RenderTemplateData> {
        val template = carboneClient.fetchTemplate(request.templateRef)
        val sanitizedTargetFileName = template.targetFileName.replace(Regex("[\\\\/*?\"<>|]"), "_")
        val targetFileName =
            if (sanitizedTargetFileName.endsWith(".pdf", ignoreCase = true)) {
                sanitizedTargetFileName
            } else {
                "$sanitizedTargetFileName.pdf"
            }

        (request.bodyData as ObjectNode).put(
            "reportName",
            targetFileName,
        )

        val raw = carboneClient.createRenderRequest(template.templateId, request.bodyData)
        val renderId = carboneClient.parseRenderId(raw)
        val result = carboneClient.fetchRenderResult(renderId)

        return Success(
            data =
                RenderTemplateData(
                    file = result.file.inputStream(),
                    responseHeaders = result.headers,
                ),
        )
    }
}
