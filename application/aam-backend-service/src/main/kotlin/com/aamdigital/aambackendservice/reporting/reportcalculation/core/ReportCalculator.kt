package com.aamdigital.aambackendservice.reporting.reportcalculation.core

import com.aamdigital.aambackendservice.reporting.domain.ReportCalculation
import reactor.core.publisher.Mono

interface ReportCalculator {
    fun calculate(reportCalculation: ReportCalculation): Mono<ReportCalculation>
}
