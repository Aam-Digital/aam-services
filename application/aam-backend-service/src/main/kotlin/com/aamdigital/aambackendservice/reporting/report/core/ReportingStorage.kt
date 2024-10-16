package com.aamdigital.aambackendservice.reporting.report.core

import com.aamdigital.aambackendservice.domain.DomainReference
import com.aamdigital.aambackendservice.reporting.domain.ReportCalculation
import org.springframework.http.HttpHeaders
import java.io.InputStream
import java.util.*

interface ReportingStorage {
    fun fetchPendingCalculations(): List<ReportCalculation>
    fun fetchCalculations(reportReference: DomainReference): List<ReportCalculation>
    fun fetchCalculation(
        calculationReference: DomainReference
    ): Optional<ReportCalculation>

    fun storeCalculation(reportCalculation: ReportCalculation): ReportCalculation

    fun headData(calculationReference: DomainReference): HttpHeaders
    fun fetchData(calculationReference: DomainReference): InputStream

    fun isCalculationOngoing(reportReference: DomainReference): Boolean
}
