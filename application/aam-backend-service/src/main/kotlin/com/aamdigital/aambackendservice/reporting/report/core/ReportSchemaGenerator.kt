package com.aamdigital.aambackendservice.reporting.report.core

import com.aamdigital.aambackendservice.reporting.domain.Report

interface ReportSchemaGenerator {
    fun getAffectedEntities(report: Report): List<String>
}
