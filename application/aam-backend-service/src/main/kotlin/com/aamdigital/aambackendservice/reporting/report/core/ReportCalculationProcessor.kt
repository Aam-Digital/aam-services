package com.aamdigital.aambackendservice.reporting.report.core

import reactor.core.publisher.Mono

interface ReportCalculationProcessor {
    fun processNextPendingCalculation(): Mono<Unit>
}
