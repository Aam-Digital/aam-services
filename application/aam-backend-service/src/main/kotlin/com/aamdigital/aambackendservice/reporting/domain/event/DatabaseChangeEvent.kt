package com.aamdigital.aambackendservice.reporting.domain.event

import com.aamdigital.aambackendservice.events.DomainEvent

data class DatabaseChangeEvent(
    val database: String,
    val documentId: String,
    val rev: String?,
    val deleted: Boolean,
) : DomainEvent()
