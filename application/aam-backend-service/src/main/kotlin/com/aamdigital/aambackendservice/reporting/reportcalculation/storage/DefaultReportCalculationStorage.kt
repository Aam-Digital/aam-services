package com.aamdigital.aambackendservice.reporting.reportcalculation.storage

import com.aamdigital.aambackendservice.common.error.AamErrorCode
import com.aamdigital.aambackendservice.common.error.ExternalSystemException
import com.aamdigital.aambackendservice.common.error.InternalServerException
import com.aamdigital.aambackendservice.common.error.NetworkException
import com.aamdigital.aambackendservice.common.error.NotFoundException
import com.aamdigital.aambackendservice.common.couchdb.core.CouchDbClient
import com.aamdigital.aambackendservice.common.couchdb.core.getQueryParamsAllDocs
import com.aamdigital.aambackendservice.common.couchdb.dto.CouchDbRow
import com.aamdigital.aambackendservice.common.domain.DomainReference
import com.aamdigital.aambackendservice.common.domain.FileStorage
import com.aamdigital.aambackendservice.reporting.reportcalculation.ReportCalculation
import com.aamdigital.aambackendservice.reporting.reportcalculation.core.ReportCalculationStorage
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.HttpClientErrorException
import java.io.InputStream
import java.io.InterruptedIOException

data class FetchReportCalculationsResponse(
    @JsonProperty("total_rows")
    val totalRows: Int,
    val offset: Int,
    val rows: List<CouchDbRow<ReportCalculationEntity>>,
)

class DefaultReportCalculationStorage(
    private val couchDbClient: CouchDbClient,
    private val fileStorage: FileStorage,
) : ReportCalculationStorage {

    companion object {
        private const val REPORT_CALCULATION_DATABASE = "report-calculation"
    }

    enum class DefaultReportCalculationStorageError : AamErrorCode {
        NETWORK_ERROR,
        NOT_FOUND,
        UNEXPECTED,
    }

    override fun storeCalculation(
        reportCalculation: ReportCalculation,
    ): ReportCalculation {
        val successDoc = try {
            couchDbClient.putDatabaseDocument(
                database = REPORT_CALCULATION_DATABASE,
                documentId = reportCalculation.id,
                body = toEntity(reportCalculation),
            )
        } catch (ex: Exception) {
            throw handleException(ex)
        }

        return fetchReportCalculation(DomainReference(successDoc.id))
    }

    override fun fetchReportCalculations(report: DomainReference): List<ReportCalculation> {
        val calculations = try {
            couchDbClient.getDatabaseDocument(
                database = REPORT_CALCULATION_DATABASE,
                documentId = "_all_docs",
                getQueryParamsAllDocs("ReportCalculation"),
                FetchReportCalculationsResponse::class
            )
        } catch (ex: Exception) {
            throw handleException(ex)
        }

        return calculations.rows
            .filter { entity ->
                entity.doc.report.id == report.id
            }
            .map { entity ->
                fromEntity(entity.doc)
            }
    }

    @Throws(
        NotFoundException::class,
        ExternalSystemException::class,
        NetworkException::class
    )
    override fun fetchReportCalculation(calculation: DomainReference): ReportCalculation {
        return try {
            fromEntity(
                couchDbClient.getDatabaseDocument(
                    database = REPORT_CALCULATION_DATABASE,
                    documentId = calculation.id,
                    queryParams = LinkedMultiValueMap(),
                    kClass = ReportCalculationEntity::class
                )
            )
        } catch (ex: Exception) {
            throw handleException(ex)
        }
    }

    override fun addReportCalculationData(
        reportCalculation: ReportCalculation,
        file: InputStream,
    ): ReportCalculation {
        try {
            fileStorage.storeFile(
                path = "report-calculation/${reportCalculation.id}",
                fileName = "data.json",
                file = file,
            )
        } catch (ex: Exception) {
            throw handleException(ex)
        }

        return fetchReportCalculation(DomainReference(reportCalculation.id))
    }

    private fun toEntity(doc: ReportCalculation): ReportCalculationEntity {
        return ReportCalculationEntity(
            id = doc.id,
            report = doc.report,
            status = doc.status,
            errorDetails = doc.errorDetails,
            calculationStarted = doc.calculationStarted,
            calculationCompleted = doc.calculationCompleted,
            args = doc.args,
            attachments = doc.attachments,
        )
    }

    private fun fromEntity(doc: ReportCalculationEntity): ReportCalculation {
        return ReportCalculation(
            id = doc.id,
            report = doc.report,
            status = doc.status,
            errorDetails = doc.errorDetails,
            calculationStarted = doc.calculationStarted,
            calculationCompleted = doc.calculationCompleted,
            args = doc.args,
            attachments = doc.attachments,
        )
    }

    private fun handleException(ex: Exception): Throwable {
        return when (ex) {
            is NotFoundException -> NotFoundException(
                message = ex.localizedMessage,
                cause = ex,
                code = DefaultReportCalculationStorageError.NOT_FOUND
            )

            is InterruptedIOException -> NetworkException(
                message = ex.localizedMessage,
                cause = ex,
                code = DefaultReportCalculationStorageError.NETWORK_ERROR
            )

            is HttpClientErrorException -> NetworkException(
                message = ex.localizedMessage,
                cause = ex,
                code = DefaultReportCalculationStorageError.NETWORK_ERROR
            )

            else -> InternalServerException(
                message = ex.localizedMessage,
                code = DefaultReportCalculationStorageError.UNEXPECTED,
                cause = ex,
            )
        }
    }
}
