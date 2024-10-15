package com.aamdigital.aambackendservice.reporting.changes.core

import com.aamdigital.aambackendservice.reporting.domain.event.DatabaseChangeEvent

interface CreateDocumentChangeUseCase {
    fun createEvent(event: DatabaseChangeEvent)
}
