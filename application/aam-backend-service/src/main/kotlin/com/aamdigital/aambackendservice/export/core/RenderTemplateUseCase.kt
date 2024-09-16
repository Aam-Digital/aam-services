package com.aamdigital.aambackendservice.export.core

import com.aamdigital.aambackendservice.domain.DomainReference
import com.aamdigital.aambackendservice.domain.DomainUseCase
import com.aamdigital.aambackendservice.domain.UseCaseData
import com.aamdigital.aambackendservice.domain.UseCaseErrorCode
import com.aamdigital.aambackendservice.domain.UseCaseRequest
import com.fasterxml.jackson.databind.JsonNode
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.http.HttpHeaders

data class RenderTemplateRequest(
    val templateRef: DomainReference,
    val bodyData: JsonNode,
) : UseCaseRequest

data class RenderTemplateData(
    val file: DataBuffer,
    val responseHeaders: HttpHeaders,
) : UseCaseData

enum class RenderTemplateErrorCode : UseCaseErrorCode {
    INTERNAL_SERVER_ERROR,
    FETCH_TEMPLATE_FAILED_ERROR,
    CREATE_RENDER_REQUEST_FAILED_ERROR,
    FETCH_RENDER_ID_REQUEST_FAILED_ERROR,
    PARSE_RESPONSE_ERROR,
    NOT_FOUND_ERROR
}

interface RenderTemplateUseCase :
    DomainUseCase<RenderTemplateRequest, RenderTemplateData, RenderTemplateErrorCode>
