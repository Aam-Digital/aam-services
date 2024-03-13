package com.aamdigital.aambackendservice.reporting.report.core

import com.aamdigital.aambackendservice.domain.DomainReference
import com.aamdigital.aambackendservice.reporting.domain.Report
import com.aamdigital.aambackendservice.reporting.domain.ReportCalculation
import com.aamdigital.aambackendservice.reporting.domain.ReportData
import reactor.core.publisher.Mono
import java.util.*

interface ReportingStorage {
    fun fetchAllReports(mode: String): Mono<List<Report>>
    fun fetchReport(
        report: DomainReference
    ): Mono<Optional<Report>>

    fun fetchPendingCalculations(): Mono<List<ReportCalculation>>
    fun fetchCalculations(reportReference: DomainReference): Mono<List<ReportCalculation>>
    fun fetchCalculation(
        calculationReference: DomainReference
    ): Mono<Optional<ReportCalculation>>

    fun storeCalculation(reportCalculation: ReportCalculation): Mono<ReportCalculation>
    fun storeData(reportData: ReportData): Mono<ReportData>
    fun fetchData(calculationReference: DomainReference): Mono<Optional<ReportData>>
    fun isCalculationOngoing(reportReference: DomainReference): Mono<Boolean>
}
