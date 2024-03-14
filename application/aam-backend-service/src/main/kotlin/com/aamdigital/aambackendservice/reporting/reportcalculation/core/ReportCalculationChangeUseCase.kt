package com.aamdigital.aambackendservice.reporting.reportcalculation.core

import com.aamdigital.aambackendservice.reporting.domain.event.DocumentChangeEvent
import reactor.core.publisher.Mono

interface ReportCalculationChangeUseCase {
    fun handle(documentChangeEvent: DocumentChangeEvent): Mono<Unit>
}
