package com.aamdigital.aambackendservice.reporting.report.core

import com.aamdigital.aambackendservice.reporting.report.Report

interface ReportQueryAnalyser {
    fun getAffectedEntities(report: Report): List<String>
}
