package com.aamdigital.aambackendservice.domain

data class Report(
    val id: String,
    val name: String,
    val mode: String?,
    val schema: ReportSchema?,
    val query: String,
)
