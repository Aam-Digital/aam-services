package com.aamdigital.aambackendservice.reporting.changes.core

import com.aamdigital.aambackendservice.reporting.domain.event.DatabaseChangeEvent
import reactor.core.publisher.Mono

interface CreateDocumentChangeUseCase {
    fun createEvent(event: DatabaseChangeEvent): Mono<Unit>
}
