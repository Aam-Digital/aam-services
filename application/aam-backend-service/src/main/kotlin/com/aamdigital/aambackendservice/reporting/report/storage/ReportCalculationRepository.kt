package com.aamdigital.aambackendservice.reporting.report.storage

import com.aamdigital.aambackendservice.couchdb.core.CouchDbStorage
import com.aamdigital.aambackendservice.couchdb.core.getQueryParamsAllDocs
import com.aamdigital.aambackendservice.couchdb.dto.DocSuccess
import com.aamdigital.aambackendservice.domain.DomainReference
import com.aamdigital.aambackendservice.error.NotFoundException
import com.aamdigital.aambackendservice.reporting.domain.ReportCalculation
import com.aamdigital.aambackendservice.reporting.domain.ReportCalculationOutcome
import com.aamdigital.aambackendservice.reporting.domain.ReportData
import org.springframework.stereotype.Service
import org.springframework.util.LinkedMultiValueMap
import reactor.core.publisher.Mono
import java.util.*

@Service
class ReportCalculationRepository(
    private val couchDbStorage: CouchDbStorage,
) {
    companion object {
        private const val REPORT_CALCULATION_DATABASE = "report-calculation"
    }

    fun storeCalculation(
        reportCalculation: ReportCalculation,
    ): Mono<DocSuccess> {
        return couchDbStorage.putDatabaseDocument(
            database = REPORT_CALCULATION_DATABASE,
            documentId = reportCalculation.id,
            body = reportCalculation,
        )
    }

    fun fetchCalculations(): Mono<FetchReportCalculationsResponse> {
        return couchDbStorage.getDatabaseDocument(
            database = REPORT_CALCULATION_DATABASE,
            documentId = "_all_docs",
            getQueryParamsAllDocs("ReportCalculation"),
            FetchReportCalculationsResponse::class
        )
    }

    fun fetchCalculation(calculationReference: DomainReference): Mono<Optional<ReportCalculation>> {
        return couchDbStorage.getDatabaseDocument(
            database = REPORT_CALCULATION_DATABASE,
            documentId = calculationReference.id,
            queryParams = LinkedMultiValueMap(),
            kClass = ReportCalculation::class
        )
            .map { Optional.of(it) }
            .defaultIfEmpty(Optional.empty())
            .onErrorReturn(Optional.empty<ReportCalculation>())
    }

    fun storeData(data: ReportData): Mono<ReportData> {
        return couchDbStorage.putDatabaseDocument(
            database = REPORT_CALCULATION_DATABASE,
            documentId = data.id,
            body = data,
        )
            .flatMap { fetchCalculation(data.calculation) }
            .flatMap {
                val calculation = it.orElseThrow {
                    NotFoundException()
                }

                calculation.outcome = ReportCalculationOutcome.Success(
                    resultHash = data.getDataHash()
                )

                couchDbStorage.putDatabaseDocument(
                    database = REPORT_CALCULATION_DATABASE,
                    documentId = calculation.id,
                    body = calculation
                )
            }.map { data }
    }

    fun fetchData(calculationReference: DomainReference): Mono<Optional<ReportData>> {
        return fetchCalculation(calculationReference)
            .map {
                val calculation = it.orElseThrow {
                    NotFoundException()
                }
                calculation.id
            }
            .flatMap { calculationId ->
                couchDbStorage.find(
                    database = "report-calculation",
                    body = mapOf(
                        Pair(
                            "selector", mapOf(
                                Pair(
                                    "calculation.id", mapOf(
                                        Pair("\$eq", calculationId)
                                    )
                                )
                            )
                        )
                    ),
                    queryParams = LinkedMultiValueMap(),
                    kClass = ReportData::class
                )
            }
            .map {
                if (it.docs.size == 1) {
                    Optional.of(it.docs.first())
                } else {
                    Optional.empty()
                }
            }
    }
}
