package com.aamdigital.aambackendservice.reporting.report.calculation.core

import com.aamdigital.aambackendservice.domain.event.DocumentChangeEvent
import reactor.core.publisher.Mono

interface ReportCalculationChangeUseCase {
    fun handle(documentChangeEvent: DocumentChangeEvent): Mono<Unit>
}
