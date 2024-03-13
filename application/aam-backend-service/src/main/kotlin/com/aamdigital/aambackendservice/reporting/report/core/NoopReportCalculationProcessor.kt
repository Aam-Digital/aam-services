package com.aamdigital.aambackendservice.reporting.report.core

import reactor.core.publisher.Mono

class NoopReportCalculationProcessor : ReportCalculationProcessor {
    override fun processNextPendingCalculation(): Mono<Unit> {
        return Mono.just(Unit)
    }
}
