package com.aamdigital.aambackendservice.common.changes

import com.aamdigital.aambackendservice.common.events.DomainEvent

/**
 * Domain event representing a single document change in a CouchDB database.
 * Carries both the current and previous version of the document for consumers
 * to determine what changed.
 */
data class DocumentChangeEvent(
    val database: String,
    val documentId: String,
    val rev: String,
    val currentVersion: Map<*, *>,
    val previousVersion: Map<*, *>,
    val deleted: Boolean
) : DomainEvent()
