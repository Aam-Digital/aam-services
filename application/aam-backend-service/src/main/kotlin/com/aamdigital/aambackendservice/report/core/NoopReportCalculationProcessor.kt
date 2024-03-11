package com.aamdigital.aambackendservice.report.core

import reactor.core.publisher.Mono

class NoopReportCalculationProcessor : ReportCalculationProcessor {
    override fun processNextPendingCalculation(): Mono<Unit> {
        return Mono.just(Unit)
    }
}
