package com.aamdigital.aambackendservice.domain.event

data class DocumentChangeEvent(
    val database: String,
    val documentId: String,
    val rev: String,
    val currentVersion: Map<*, *>,
    val previousVersion: Map<*, *>,
    val deleted: Boolean
)
