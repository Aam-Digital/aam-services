package com.aamdigital.aambackendservice.reporting.domain.event

data class DocumentChangeEvent(
    val database: String,
    val documentId: String,
    val rev: String,
    val currentVersion: Map<*, *>, // Todo: does not work with big data
    val previousVersion: Map<*, *>, // Todo: does not work with big data
    val deleted: Boolean
)
