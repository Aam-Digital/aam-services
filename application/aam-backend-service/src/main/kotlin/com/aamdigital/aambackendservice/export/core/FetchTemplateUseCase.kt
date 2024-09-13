package com.aamdigital.aambackendservice.export.core

import com.aamdigital.aambackendservice.domain.DomainReference
import com.aamdigital.aambackendservice.domain.DomainUseCase
import com.aamdigital.aambackendservice.domain.UseCaseData
import com.aamdigital.aambackendservice.domain.UseCaseErrorCode
import com.aamdigital.aambackendservice.domain.UseCaseRequest
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.http.HttpHeaders

data class FetchTemplateRequest(
    val templateRef: DomainReference,
) : UseCaseRequest

data class FetchTemplateData(
    val file: DataBuffer,
    val responseHeaders: HttpHeaders,
) : UseCaseData


enum class FetchTemplateErrorCode : UseCaseErrorCode {
    INTERNAL_SERVER_ERROR,
    PARSE_RESPONSE_ERROR,
    FETCH_TEMPLATE_REQUEST_FAILED_ERROR
}

interface FetchTemplateUseCase :
    DomainUseCase<FetchTemplateRequest, FetchTemplateData, FetchTemplateErrorCode>
