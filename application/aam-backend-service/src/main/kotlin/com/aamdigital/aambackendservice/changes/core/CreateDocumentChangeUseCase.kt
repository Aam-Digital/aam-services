package com.aamdigital.aambackendservice.changes.core

import com.aamdigital.aambackendservice.changes.core.event.DatabaseChangeEvent
import reactor.core.publisher.Mono

interface CreateDocumentChangeUseCase {
    fun createEvent(event: DatabaseChangeEvent): Mono<Unit>
}
