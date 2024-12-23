package com.aamdigital.aambackendservice.changes.domain

import com.aamdigital.aambackendservice.events.DomainEvent

data class DocumentChangeEvent(
    val database: String,
    val documentId: String,
    val rev: String,
    val currentVersion: Map<*, *>,
    val previousVersion: Map<*, *>,
    val deleted: Boolean
) : DomainEvent()
