package com.aamdigital.aambackendservice.reporting.domain.event

data class DatabaseChangeEvent(
    val database: String,
    val documentId: String,
    val rev: String?,
    val deleted: Boolean,
)
