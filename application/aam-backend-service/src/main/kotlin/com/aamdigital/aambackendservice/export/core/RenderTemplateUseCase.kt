package com.aamdigital.aambackendservice.export.core

import com.aamdigital.aambackendservice.common.domain.DomainReference
import com.aamdigital.aambackendservice.common.domain.DomainUseCase
import com.aamdigital.aambackendservice.common.domain.UseCaseData
import com.aamdigital.aambackendservice.common.domain.UseCaseRequest
import com.aamdigital.aambackendservice.common.error.AamErrorCode
import com.fasterxml.jackson.databind.JsonNode
import org.springframework.http.HttpHeaders
import java.io.InputStream

data class RenderTemplateRequest(
    val templateRef: DomainReference,
    val bodyData: JsonNode,
) : UseCaseRequest

data class RenderTemplateData(
    val file: InputStream,
    val responseHeaders: HttpHeaders,
) : UseCaseData

enum class RenderTemplateError : AamErrorCode {
    INTERNAL_SERVER_ERROR,
    FETCH_TEMPLATE_FAILED_ERROR,
    CREATE_RENDER_REQUEST_FAILED_ERROR,
    FETCH_RENDER_ID_REQUEST_FAILED_ERROR,
    PARSE_RESPONSE_ERROR,
    NOT_FOUND_ERROR;
}

abstract class RenderTemplateUseCase :
    DomainUseCase<RenderTemplateRequest, RenderTemplateData>()
