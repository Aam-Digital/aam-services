package com.aamdigital.aambackendservice.reporting.report.core

import com.aamdigital.aambackendservice.reporting.domain.Report

interface ReportSchemaGenerator {
    fun getTableNamesByQuery(query: String): List<String>
    fun getAffectedEntities(report: Report): List<String>
}
