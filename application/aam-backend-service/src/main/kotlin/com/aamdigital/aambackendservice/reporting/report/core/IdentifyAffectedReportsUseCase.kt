package com.aamdigital.aambackendservice.reporting.report.core

import com.aamdigital.aambackendservice.domain.DomainReference
import com.aamdigital.aambackendservice.reporting.domain.event.DocumentChangeEvent
import reactor.core.publisher.Mono

interface IdentifyAffectedReportsUseCase {
    fun analyse(documentChangeEvent: DocumentChangeEvent): Mono<List<DomainReference>>
}
