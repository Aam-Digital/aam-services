package com.aamdigital.aambackendservice.common.changes.core

import com.aamdigital.aambackendservice.common.changes.domain.DatabaseChangeEvent

interface CreateDocumentChangeUseCase {
    fun createEvent(event: DatabaseChangeEvent)
}
