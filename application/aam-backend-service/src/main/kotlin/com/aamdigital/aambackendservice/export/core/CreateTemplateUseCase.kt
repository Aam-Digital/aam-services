package com.aamdigital.aambackendservice.export.core

import com.aamdigital.aambackendservice.common.domain.DomainReference
import com.aamdigital.aambackendservice.common.domain.DomainUseCase
import com.aamdigital.aambackendservice.common.domain.UseCaseData
import com.aamdigital.aambackendservice.common.domain.UseCaseRequest
import com.aamdigital.aambackendservice.common.error.AamErrorCode
import org.springframework.web.multipart.MultipartFile

data class CreateTemplateRequest(
    val file: MultipartFile,
) : UseCaseRequest

data class CreateTemplateData(
    val templateRef: DomainReference,
) : UseCaseData


enum class CreateTemplateError : AamErrorCode {
    INTERNAL_SERVER_ERROR,
    PARSE_RESPONSE_ERROR,
    CREATE_TEMPLATE_REQUEST_FAILED_ERROR
}

abstract class CreateTemplateUseCase :
    DomainUseCase<CreateTemplateRequest, CreateTemplateData>()
