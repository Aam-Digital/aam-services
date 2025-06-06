package com.aamdigital.aambackendservice.reporting.report.core

import com.aamdigital.aambackendservice.common.domain.DomainReference
import com.aamdigital.aambackendservice.common.error.AamErrorCode
import com.aamdigital.aambackendservice.common.error.AamException
import com.aamdigital.aambackendservice.reporting.report.Report

enum class ReportStorageErrorCode : AamErrorCode {
    INVALID_REPORT_CONFIG,
    PARSING_ERROR,
}

interface ReportStorage {
    @Throws(AamException::class)
    fun fetchAllReports(mode: String): List<Report>

    @Throws(AamException::class)
    fun fetchReport(reportRef: DomainReference): Report
}
