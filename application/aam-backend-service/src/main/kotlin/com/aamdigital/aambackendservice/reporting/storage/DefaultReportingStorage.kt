package com.aamdigital.aambackendservice.reporting.storage

import com.aamdigital.aambackendservice.couchdb.dto.CouchDbChange
import com.aamdigital.aambackendservice.domain.DomainReference
import com.aamdigital.aambackendservice.error.NotFoundException
import com.aamdigital.aambackendservice.reporting.domain.Report
import com.aamdigital.aambackendservice.reporting.domain.ReportCalculation
import com.aamdigital.aambackendservice.reporting.domain.ReportCalculationStatus
import com.aamdigital.aambackendservice.reporting.domain.ReportData
import com.aamdigital.aambackendservice.reporting.domain.ReportSchema
import com.aamdigital.aambackendservice.reporting.report.core.ReportSchemaGenerator
import com.aamdigital.aambackendservice.reporting.report.core.ReportingStorage
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.stereotype.Service
import org.springframework.util.LinkedMultiValueMap
import reactor.core.publisher.Mono
import java.util.*

data class ReportCalculationEntity(
    val id: String,
    val key: String,
    val value: CouchDbChange,
    val doc: ReportCalculation,
)

data class FetchReportCalculationsResponse(
    @JsonProperty("total_rows")
    val totalRows: Int,
    val offset: Int,
    val rows: List<ReportCalculationEntity>,
)

@Service
class DefaultReportingStorage(
    private val reportRepository: ReportRepository,
    private val reportCalculationRepository: ReportCalculationRepository,
    private val reportSchemaGenerator: ReportSchemaGenerator,
) : ReportingStorage {
    override fun fetchAllReports(mode: String): Mono<List<Report>> {
        return reportRepository.fetchReports()
            .map { response ->
                if (response.rows.isEmpty()) {
                    return@map emptyList()
                }

                response.rows.filter {
                    it.doc.mode == mode
                }.map {
                    Report(
                        id = it.id,
                        name = it.doc.title,
                        mode = it.doc.mode,
                        query = it.doc.aggregationDefinition ?: "",
                        schema = ReportSchema(
                            fields = reportSchemaGenerator.getTableNamesByQuery(it.doc.aggregationDefinition ?: "")
                        )
                    )
                }
            }
    }

    override fun fetchReport(report: DomainReference): Mono<Optional<Report>> {
        return reportRepository.fetchReport(
            documentId = report.id,
            queryParams = LinkedMultiValueMap(),
        ).map { reportDoc ->
            Optional.of(
                Report(
                    id = reportDoc.id,
                    name = reportDoc.title,
                    query = reportDoc.aggregationDefinition ?: "",
                    mode = reportDoc.mode,
                    schema = ReportSchema(
                        fields = reportSchemaGenerator.getTableNamesByQuery(reportDoc.aggregationDefinition ?: "")
                    )
                )
            )
        }
            .onErrorReturn(Optional.empty())
    }

    override fun fetchPendingCalculations(): Mono<List<ReportCalculation>> {
        return reportCalculationRepository.fetchCalculations()
            .map { response ->
                response.rows
                    .filter { reportCalculationEntity ->
                        reportCalculationEntity.doc.status == ReportCalculationStatus.PENDING
                    }
                    .map { reportCalculationEntity -> mapFromEntity(reportCalculationEntity) }
            }
    }

    override fun fetchCalculations(reportReference: DomainReference): Mono<List<ReportCalculation>> {
        return reportCalculationRepository.fetchCalculations()
            .map { response ->
                response.rows
                    .filter { entity ->
                        entity.doc.report.id == reportReference.id
                    }
                    .map { entity ->
                        mapFromEntity(entity)
                    }
            }
    }

    override fun fetchCalculation(calculationReference: DomainReference): Mono<Optional<ReportCalculation>> {
        return reportCalculationRepository.fetchCalculation(calculationReference)
    }

    override fun storeCalculation(reportCalculation: ReportCalculation): Mono<ReportCalculation> {
        return reportCalculationRepository.storeCalculation(reportCalculation = reportCalculation)
            .flatMap { entity ->
                fetchCalculation(DomainReference(entity.id))
            }
            .map {
                it.orElseThrow { NotFoundException() }
            }
    }

    override fun storeData(reportData: ReportData): Mono<ReportData> {
        return reportCalculationRepository.storeData(reportData)
    }

    override fun fetchData(calculationReference: DomainReference): Mono<Optional<ReportData>> {
        return reportCalculationRepository.fetchData(calculationReference)
    }

    override fun isCalculationOngoing(reportReference: DomainReference): Mono<Boolean> {
        return reportCalculationRepository.fetchCalculations()
            .map { response ->
                response.rows
                    .filter { reportCalculation ->
                        reportCalculation.doc.report.id == reportReference.id
                    }.any { reportCalculation ->
                        reportCalculation.doc.status == ReportCalculationStatus.PENDING ||
                                reportCalculation.doc.status == ReportCalculationStatus.RUNNING

                    }
            }
    }

    private fun mapFromEntity(entity: ReportCalculationEntity): ReportCalculation = ReportCalculation(
        id = entity.doc.id,
        report = entity.doc.report,
        status = entity.doc.status,
        startDate = entity.doc.startDate,
        endDate = entity.doc.endDate,
        params = entity.doc.params,
        outcome = entity.doc.outcome
    )
}
