package com.aamdigital.aambackendservice.reporting.report.storage

import com.aamdigital.aambackendservice.common.couchdb.core.CouchDbClient
import com.aamdigital.aambackendservice.common.couchdb.core.getQueryParamsAllDocs
import com.aamdigital.aambackendservice.common.couchdb.dto.CouchDbSearchResponse
import com.aamdigital.aambackendservice.common.domain.DomainReference
import com.aamdigital.aambackendservice.common.error.InternalServerException
import com.aamdigital.aambackendservice.common.error.InvalidArgumentException
import com.aamdigital.aambackendservice.common.error.NetworkException
import com.aamdigital.aambackendservice.common.error.NotFoundException
import com.aamdigital.aambackendservice.reporting.report.Report
import com.aamdigital.aambackendservice.reporting.report.ReportItem
import com.aamdigital.aambackendservice.reporting.report.core.ReportStorage
import com.aamdigital.aambackendservice.reporting.report.core.ReportStorageErrorCode
import com.aamdigital.aambackendservice.reporting.reportcalculation.storage.DefaultReportCalculationStorage.DefaultReportCalculationStorageError
import com.fasterxml.jackson.core.JacksonException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.springframework.util.LinkedMultiValueMap
import java.io.InterruptedIOException

/**
 * Fetches ReportConfig: documents from CouchDB and normalizes them into [Report] domain objects.
 *
 * Accepts both the legacy v1 format (aggregationDefinition + neededArgs + positional/named placeholders)
 * and the canonical format (reportDefinition + transformations + $named placeholders).
 * Legacy docs are normalized in memory and written back to CouchDB in canonical form on first read.
 */
class DefaultReportStorage(
    private val couchDbClient: CouchDbClient,
    private val objectMapper: ObjectMapper
) : ReportStorage {
    private val legacyMigration = ReportConfigLegacyMigration(couchDbClient)

    companion object {
        private const val REPORT_DATABASE = "app"
    }

    override fun fetchAllReports(mode: String): List<Report> {
        // todo: paginate requests
        val response =
            couchDbClient.getDatabaseDocument(
                database = REPORT_DATABASE,
                documentId = "_all_docs",
                getQueryParamsAllDocs("ReportConfig"),
                CouchDbSearchResponse::class
            )

        if (response.rows.isEmpty()) {
            return emptyList()
        }

        return response.rows
            .filter { isSqlReport(it.doc) }
            .map { normalizeRawDocToReport(it.doc) }
    }

    @Throws(
        InvalidArgumentException::class,
        NetworkException::class,
        InternalServerException::class,
        NotFoundException::class
    )
    override fun fetchReport(reportRef: DomainReference): Report {
        val rawDoc =
            try {
                couchDbClient.getDatabaseDocument(
                    database = REPORT_DATABASE,
                    documentId = reportRef.id,
                    queryParams = LinkedMultiValueMap(),
                    kClass = ObjectNode::class
                )
            } catch (ex: Exception) {
                throw handleException(ex)
            }

        if (!isSqlReport(rawDoc)) {
            throw InvalidArgumentException(
                message = "Non SQL Reports are not supported",
                code = ReportStorageErrorCode.INVALID_REPORT_CONFIG
            )
        }

        val report = normalizeRawDocToReport(rawDoc)

        if (legacyMigration.isLegacyDoc(rawDoc)) {
            legacyMigration.tryWriteMigratedDoc(rawDoc, report)
        }

        return report
    }

    private fun normalizeRawDocToReport(doc: ObjectNode): Report =
        if (legacyMigration.isLegacyDoc(doc)) {
            legacyMigration.normalizeLegacyDoc(doc)
        } else {
            normalizeCanonicalDoc(doc)
        }

    private fun isSqlReport(doc: ObjectNode): Boolean = doc.hasNonNull("mode") && doc.get("mode").textValue() == "sql"

    private fun normalizeCanonicalDoc(doc: ObjectNode): Report {
        val entity =
            try {
                objectMapper.treeToValue(doc, ReportConfigEntity::class.java)
            } catch (ex: JacksonException) {
                throw InvalidArgumentException(
                    message = "Could not parse Report to Entity",
                    code = ReportStorageErrorCode.PARSING_ERROR,
                    cause = ex
                )
            }

        return Report(
            id = entity.id,
            title = entity.title,
            items = entity.reportDefinition.map { asReportItem(it) },
            transformations = entity.transformations ?: emptyMap()
        )
    }

    private fun asReportItem(dto: ReportDefinitionDto): ReportItem {
        if (!dto.query.isNullOrEmpty()) {
            return ReportItem.ReportQuery(sql = dto.query)
        }

        if (!dto.items.isNullOrEmpty() && !dto.groupTitle.isNullOrEmpty()) {
            return ReportItem.ReportGroup(
                title = dto.groupTitle,
                items = dto.items.map { asReportItem(it) }
            )
        }

        throw InternalServerException(
            message = "Invalid ReportConfig. Could not parse ReportItem",
            code = ReportStorageErrorCode.INVALID_REPORT_CONFIG
        )
    }

    private fun handleException(ex: Exception): Throwable =
        when (ex) {
            is JacksonException ->
                InvalidArgumentException(
                    message = "Could not parse Report to Entity",
                    code = ReportStorageErrorCode.PARSING_ERROR,
                    cause = ex
                )

            is InterruptedIOException ->
                NetworkException(
                    message = ex.localizedMessage,
                    cause = ex,
                    code = DefaultReportCalculationStorageError.NETWORK_ERROR
                )

            else -> ex
        }
}
