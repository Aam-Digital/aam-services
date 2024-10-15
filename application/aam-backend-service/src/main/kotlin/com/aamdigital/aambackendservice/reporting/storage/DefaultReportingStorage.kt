package com.aamdigital.aambackendservice.reporting.storage

import com.aamdigital.aambackendservice.couchdb.dto.CouchDbChange
import com.aamdigital.aambackendservice.domain.DomainReference
import com.aamdigital.aambackendservice.error.AamErrorCode
import com.aamdigital.aambackendservice.error.NotFoundException
import com.aamdigital.aambackendservice.reporting.domain.ReportCalculation
import com.aamdigital.aambackendservice.reporting.domain.ReportCalculationStatus
import com.aamdigital.aambackendservice.reporting.report.core.ReportingStorage
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Service
import java.io.InputStream
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
@Deprecated("use sub storages directly")
class DefaultReportingStorage(
    private val reportCalculationRepository: ReportCalculationRepository,
) : ReportingStorage {

    enum class DefaultReportingStorageErrorCode : AamErrorCode {
        NOT_FOUND
    }

    override fun fetchPendingCalculations(): List<ReportCalculation> {
        val calculations = reportCalculationRepository.fetchCalculations()
        return calculations.rows
            .filter { reportCalculationEntity ->
                reportCalculationEntity.doc.status == ReportCalculationStatus.PENDING
            }
            .map { reportCalculationEntity -> mapFromEntity(reportCalculationEntity) }
    }

    override fun fetchCalculations(reportReference: DomainReference): List<ReportCalculation> {
        val calculations = reportCalculationRepository.fetchCalculations()
        return calculations.rows
            .filter { entity ->
                entity.doc.report.id == reportReference.id
            }
            .map { entity ->
                mapFromEntity(entity)
            }
    }

    override fun fetchCalculation(calculationReference: DomainReference): Optional<ReportCalculation> {
        return reportCalculationRepository.fetchCalculation(calculationReference)
    }

    override fun storeCalculation(reportCalculation: ReportCalculation): ReportCalculation {
        val doc = reportCalculationRepository.storeCalculation(reportCalculation = reportCalculation)

        return fetchCalculation(DomainReference(doc.id)).orElseThrow {
            NotFoundException(
                message = "Calculation not found",
                code = DefaultReportingStorageErrorCode.NOT_FOUND
            )
        }
    }

    override fun fetchData(calculationReference: DomainReference): InputStream {
        return reportCalculationRepository.fetchData(calculationReference)
    }

    override fun headData(calculationReference: DomainReference): HttpHeaders {
        return reportCalculationRepository.headData(calculationReference)
    }

    override fun isCalculationOngoing(reportReference: DomainReference): Boolean {
        val calculations = reportCalculationRepository.fetchCalculations()
        return calculations.rows
            .filter { reportCalculation ->
                reportCalculation.doc.report.id == reportReference.id
            }.any { reportCalculation ->
                reportCalculation.doc.status == ReportCalculationStatus.PENDING ||
                        reportCalculation.doc.status == ReportCalculationStatus.RUNNING

            }
    }

    private fun mapFromEntity(entity: ReportCalculationEntity): ReportCalculation = ReportCalculation(
        id = entity.doc.id,
        report = entity.doc.report,
        status = entity.doc.status,
        calculationStarted = entity.doc.calculationStarted,
        calculationCompleted = entity.doc.calculationCompleted,
        args = entity.doc.args,
        attachments = entity.doc.attachments
    )
}
