package com.aamdigital.aambackendservice.export.core

import com.aamdigital.aambackendservice.common.domain.DomainReference
import com.aamdigital.aambackendservice.common.domain.DomainUseCase
import com.aamdigital.aambackendservice.common.domain.UseCaseData
import com.aamdigital.aambackendservice.common.domain.UseCaseRequest
import com.aamdigital.aambackendservice.common.error.AamErrorCode
import org.springframework.http.HttpHeaders
import java.io.InputStream

data class FetchTemplateRequest(
    val templateRef: DomainReference,
) : UseCaseRequest

data class FetchTemplateData(
    val file: InputStream,
    val responseHeaders: HttpHeaders,
) : UseCaseData


enum class FetchTemplateError : AamErrorCode {
    INTERNAL_SERVER_ERROR,
    FETCH_TEMPLATE_REQUEST_FAILED_ERROR,
    NOT_FOUND_ERROR
}

abstract class FetchTemplateUseCase :
    DomainUseCase<FetchTemplateRequest, FetchTemplateData>()
