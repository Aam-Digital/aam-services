package com.aamdigital.aambackendservice.export.core

import com.aamdigital.aambackendservice.domain.DomainReference
import com.fasterxml.jackson.databind.JsonNode
import org.springframework.core.io.buffer.DataBuffer
import reactor.core.publisher.Mono

interface RenderTemplateUseCase {
    fun renderTemplate(templateRef: DomainReference, bodyData: JsonNode): Mono<DataBuffer>
}
