package com.aamdigital.aambackendservice.reporting.storage

import com.aamdigital.aambackendservice.couchdb.core.CouchDbClient
import com.aamdigital.aambackendservice.couchdb.core.getQueryParamsAllDocs
import com.aamdigital.aambackendservice.couchdb.dto.DocSuccess
import com.aamdigital.aambackendservice.domain.DomainReference
import com.aamdigital.aambackendservice.reporting.domain.ReportCalculation
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Service
import org.springframework.util.LinkedMultiValueMap
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*

@Service
class ReportCalculationRepository(
    private val couchDbClient: CouchDbClient,
) {
    companion object {
        private const val REPORT_CALCULATION_DATABASE = "report-calculation"
    }

    fun storeCalculation(
        reportCalculation: ReportCalculation,
    ): Mono<DocSuccess> {
        return couchDbClient.putDatabaseDocument(
            database = REPORT_CALCULATION_DATABASE,
            documentId = reportCalculation.id,
            body = reportCalculation,
        )
    }

    fun fetchCalculations(): Mono<FetchReportCalculationsResponse> {
        return couchDbClient.getDatabaseDocument(
            database = REPORT_CALCULATION_DATABASE,
            documentId = "_all_docs",
            getQueryParamsAllDocs("ReportCalculation"),
            FetchReportCalculationsResponse::class
        )
    }

    fun fetchCalculation(calculationReference: DomainReference): Mono<Optional<ReportCalculation>> {
        return couchDbClient.getDatabaseDocument(
            database = REPORT_CALCULATION_DATABASE,
            documentId = calculationReference.id,
            queryParams = LinkedMultiValueMap(),
            kClass = ReportCalculation::class
        )
            .map { Optional.of(it) }
            .defaultIfEmpty(Optional.empty())
            .onErrorReturn(Optional.empty<ReportCalculation>())
    }

    fun headData(calculationReference: DomainReference): Mono<HttpHeaders> {
        return couchDbClient.headAttachment(
            database = "report-calculation",
            documentId = calculationReference.id,
            attachmentId = "data.json",
        )
    }

    fun fetchData(calculationReference: DomainReference): Flux<DataBuffer> {
        return couchDbClient.getAttachment(
            database = "report-calculation",
            documentId = calculationReference.id,
            attachmentId = "data.json",
        )
    }
}
