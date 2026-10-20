package com.aamdigital.aambackendservice.reporting.report.storage

import com.aamdigital.aambackendservice.common.couchdb.core.CouchDbClient
import com.aamdigital.aambackendservice.common.domain.DomainReference
import com.aamdigital.aambackendservice.common.error.ExternalSystemException
import com.aamdigital.aambackendservice.common.error.InvalidArgumentException
import com.aamdigital.aambackendservice.common.error.NotFoundException
import com.aamdigital.aambackendservice.reporting.report.Report
import com.aamdigital.aambackendservice.reporting.report.ReportItem
import com.aamdigital.aambackendservice.reporting.report.core.ReportStorage
import com.aamdigital.aambackendservice.reporting.report.core.ReportStorageErrorCode
import com.aamdigital.aambackendservice.reporting.reportcalculation.storage.DefaultReportCalculationStorage.DefaultReportCalculationStorageError
import com.fasterxml.jackson.core.JacksonException
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.slf4j.LoggerFactory
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
    private val logger = LoggerFactory.getLogger(javaClass)
    private val legacyMigration = ReportConfigLegacyMigration(couchDbClient)
    private val columnRenameMigration = ReportColumnRenameMigration(couchDbClient)

    companion object {
        private const val REPORT_DATABASE = "app"
    }

    override fun fetchAllReports(mode: String): List<Report> {
        val sqlReports =
            couchDbClient.findDatabaseDocumentsByPrefix(
                database = REPORT_DATABASE,
                prefix = "ReportConfig",
                selector = mapOf("mode" to "sql"),
                kClass = ObjectNode::class
            )

        // one invalid config must not hide the valid ones, e.g. from change detection
        return sqlReports.mapNotNull { doc ->
            try {
                columnRenameMigration.migrate(normalizeRawDocToReport(doc))
            } catch (ex: InvalidArgumentException) {
                logger.warn("Skipping invalid report config {}: {}", doc.get("_id")?.textValue(), ex.message)
                null
            }
        }
    }

    @Throws(
        InvalidArgumentException::class,
        ExternalSystemException::class,
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

        val rawReport = normalizeRawDocToReport(rawDoc)
        val report = columnRenameMigration.migrate(rawReport)

        if (legacyMigration.isLegacyDoc(rawDoc)) {
            // the legacy write-back already persists the report with renamed columns
            legacyMigration.tryWriteMigratedDoc(rawDoc, report)
        } else if (report != rawReport) {
            columnRenameMigration.tryWriteRenamedDoc(rawDoc)
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
                val path = (ex as? JsonMappingException)?.pathReference
                throw InvalidArgumentException(
                    message =
                        "Could not parse ReportConfig '${doc.get("_id")?.textValue()}'" +
                            (path?.let { " at $it" } ?: ""),
                    code = ReportStorageErrorCode.PARSING_ERROR,
                    cause = ex
                )
            }

        return Report(
            id = entity.id,
            title = resolveReportTitle(entity.title),
            items =
                entity.reportDefinition.mapIndexed { index, dto ->
                    asReportItem(entity.id, dto, "reportDefinition[$index]")
                },
            transformations = entity.transformations ?: emptyMap()
        )
    }

    /**
     * @param position where [dto] sits in the config, e.g. `reportDefinition[1].items[0]`,
     *     so the error message tells the admin which entry to fix
     */
    private fun asReportItem(
        reportId: String,
        dto: ReportDefinitionDto?,
        position: String
    ): ReportItem {
        if (dto == null) {
            throw InvalidArgumentException(
                message = "Invalid ReportConfig '$reportId': $position must not be null",
                code = ReportStorageErrorCode.INVALID_REPORT_CONFIG
            )
        }

        if (!dto.query.isNullOrEmpty()) {
            return ReportItem.ReportQuery(sql = dto.query)
        }

        if (!dto.items.isNullOrEmpty() && !dto.groupTitle.isNullOrEmpty()) {
            return ReportItem.ReportGroup(
                title = dto.groupTitle,
                items =
                    dto.items.mapIndexed { index, item ->
                        asReportItem(reportId, item, "$position.items[$index]")
                    }
            )
        }

        throw InvalidArgumentException(
            message =
                "Invalid ReportConfig '$reportId': $position needs either a non-empty \"query\" " +
                    "or a \"groupTitle\" with non-empty \"items\"",
            code = ReportStorageErrorCode.INVALID_REPORT_CONFIG
        )
    }

    private fun handleException(ex: Exception): Throwable =
        when (ex) {
            is JacksonException -> {
                InvalidArgumentException(
                    message = "Could not parse Report to Entity",
                    code = ReportStorageErrorCode.PARSING_ERROR,
                    cause = ex
                )
            }

            is InterruptedIOException -> {
                ExternalSystemException(
                    message = ex.localizedMessage,
                    cause = ex,
                    code = DefaultReportCalculationStorageError.NETWORK_ERROR
                )
            }

            else -> {
                ex
            }
        }
}
