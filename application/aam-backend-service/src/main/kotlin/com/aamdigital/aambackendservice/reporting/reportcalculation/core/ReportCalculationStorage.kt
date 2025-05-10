package com.aamdigital.aambackendservice.reporting.reportcalculation.core

import com.aamdigital.aambackendservice.common.domain.DomainReference
import com.aamdigital.aambackendservice.common.error.AamException
import com.aamdigital.aambackendservice.reporting.reportcalculation.ReportCalculation
import java.io.InputStream

interface ReportCalculationStorage {

    // todo Pagination
    @Throws(AamException::class)
    fun fetchReportCalculations(report: DomainReference): List<ReportCalculation>

    @Throws(AamException::class)
    fun fetchReportCalculation(calculation: DomainReference): ReportCalculation

    @Throws(AamException::class)
    fun storeCalculation(reportCalculation: ReportCalculation): ReportCalculation

    @Throws(AamException::class)
    fun addReportCalculationData(
        reportCalculation: ReportCalculation,
        file: InputStream
    ): ReportCalculation
}
