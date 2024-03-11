package com.aamdigital.aambackendservice.changes.core.event

data class DatabaseChangeEvent(
    val database: String,
    val documentId: String,
    val rev: String?,
    val deleted: Boolean,
)
