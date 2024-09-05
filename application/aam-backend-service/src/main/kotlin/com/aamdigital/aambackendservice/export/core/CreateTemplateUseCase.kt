package com.aamdigital.aambackendservice.export.core

import com.aamdigital.aambackendservice.domain.DomainReference
import org.springframework.http.codec.multipart.FilePart
import reactor.core.publisher.Mono

data class CreateTemplateResponse(
    val template: DomainReference,
)

data class CreateTemplateRequest(
    val file: FilePart,
)

interface CreateTemplateUseCase {
    fun createTemplate(createTemplateRequest: CreateTemplateRequest): Mono<CreateTemplateResponse>
}
