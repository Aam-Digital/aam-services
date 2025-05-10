package com.aamdigital.aambackendservice.common.changes.domain

import com.aamdigital.aambackendservice.common.events.DomainEvent

data class DatabaseChangeEvent(
    val database: String,
    val documentId: String,
    val rev: String?,
    val deleted: Boolean,
) : DomainEvent()
