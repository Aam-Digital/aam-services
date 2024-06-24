package com.aamdigital.aambackendservice.reporting.domain

data class Report(
    val id: String,
    val name: String,
    val mode: String?,
    val schema: ReportSchema?,
    val query: String,
    val neededArgs: List<String> = emptyList(),
)
