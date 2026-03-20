package com.aamdigital.aambackendservice.reporting.reportcalculation.core

import com.aamdigital.aambackendservice.common.changes.DocumentChangeEvent

interface ReportCalculationChangeUseCase {
    fun handle(documentChangeEvent: DocumentChangeEvent)
}
