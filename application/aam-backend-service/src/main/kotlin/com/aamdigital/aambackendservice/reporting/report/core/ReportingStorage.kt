package com.aamdigital.aambackendservice.reporting.report.core

import com.aamdigital.aambackendservice.domain.DomainReference
import com.aamdigital.aambackendservice.reporting.domain.ReportCalculation
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.http.HttpHeaders
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*

interface ReportingStorage {
    fun fetchPendingCalculations(): Mono<List<ReportCalculation>>
    fun fetchCalculations(reportReference: DomainReference): Mono<List<ReportCalculation>>
    fun fetchCalculation(
        calculationReference: DomainReference
    ): Mono<Optional<ReportCalculation>>

    fun storeCalculation(reportCalculation: ReportCalculation): Mono<ReportCalculation>

    fun headData(calculationReference: DomainReference): Mono<HttpHeaders>
    fun fetchData(calculationReference: DomainReference): Flux<DataBuffer>

    fun isCalculationOngoing(reportReference: DomainReference): Mono<Boolean>
}
