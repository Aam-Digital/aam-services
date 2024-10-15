package com.aamdigital.aambackendservice.reporting.reportcalculation.core

import com.aamdigital.aambackendservice.reporting.domain.event.DocumentChangeEvent

interface ReportCalculationChangeUseCase {
    fun handle(documentChangeEvent: DocumentChangeEvent)
}
