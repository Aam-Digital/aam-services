package com.aamdigital.aambackendservice.report.calculation.core

import com.aamdigital.aambackendservice.domain.ReportCalculation
import com.aamdigital.aambackendservice.domain.ReportData
import reactor.core.publisher.Mono

interface ReportCalculator {
    fun calculate(reportCalculation: ReportCalculation): Mono<ReportData>
}
