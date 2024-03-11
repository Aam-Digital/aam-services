package com.aamdigital.aambackendservice.report.core

import com.aamdigital.aambackendservice.domain.Report

interface ReportSchemaGenerator {
    fun getTableNamesByQuery(query: String): List<String>
    fun getAffectedEntities(report: Report): List<String>
}
