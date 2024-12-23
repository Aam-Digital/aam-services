package com.aamdigital.aambackendservice.changes.core

import com.aamdigital.aambackendservice.changes.domain.DatabaseChangeEvent

interface CreateDocumentChangeUseCase {
    fun createEvent(event: DatabaseChangeEvent)
}
