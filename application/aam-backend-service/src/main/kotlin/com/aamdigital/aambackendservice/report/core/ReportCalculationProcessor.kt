package com.aamdigital.aambackendservice.report.core

import reactor.core.publisher.Mono

interface ReportCalculationProcessor {
    fun processNextPendingCalculation(): Mono<Unit>
}
