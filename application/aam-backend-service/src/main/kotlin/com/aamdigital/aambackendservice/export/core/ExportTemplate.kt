package com.aamdigital.aambackendservice.export.core

data class ExportTemplate(
    val id: String,
    val templateId: String,
    val targetFileName: String,
    val title: String,
    val description: String,
    val applicableForEntityTypes: List<String>,
)
