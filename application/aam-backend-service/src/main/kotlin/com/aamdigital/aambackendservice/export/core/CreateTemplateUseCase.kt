package com.aamdigital.aambackendservice.export.core

import com.aamdigital.aambackendservice.domain.DomainReference
import com.aamdigital.aambackendservice.domain.DomainUseCase
import com.aamdigital.aambackendservice.domain.UseCaseData
import com.aamdigital.aambackendservice.domain.UseCaseErrorCode
import com.aamdigital.aambackendservice.domain.UseCaseRequest
import org.springframework.http.codec.multipart.FilePart

data class CreateTemplateRequest(
    val file: FilePart,
) : UseCaseRequest

data class CreateTemplateData(
    val templateRef: DomainReference,
) : UseCaseData


enum class CreateTemplateErrorCode : UseCaseErrorCode {
    INTERNAL_SERVER_ERROR,
    PARSE_RESPONSE_ERROR,
    CREATE_TEMPLATE_REQUEST_FAILED_ERROR
}

interface CreateTemplateUseCase :
    DomainUseCase<CreateTemplateRequest, CreateTemplateData, CreateTemplateErrorCode>
