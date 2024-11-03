package com.aamdigital.aambackendservice.reporting.domain.event

import com.aamdigital.aambackendservice.events.DomainEvent

data class DocumentChangeEvent(
    val database: String,
    val documentId: String,
    val rev: String,
    val currentVersion: Map<*, *>,
    val previousVersion: Map<*, *>,
    val deleted: Boolean
) : DomainEvent()
