package com.aamdigital.aambackendservice.export.core

import com.aamdigital.aambackendservice.domain.DomainReference
import reactor.core.publisher.Mono

interface TemplateStorage {
    fun fetchTemplate(template: DomainReference): Mono<ExportTemplate>
}
