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

        if (isLegacyDoc(rawDoc)) {
            tryWriteMigratedDoc(rawDoc, report)
        }

        return report
    }

    private fun normalizeRawDocToReport(doc: ObjectNode): Report =
        if (isLegacyDoc(doc)) normalizeLegacyDoc(doc) else normalizeCanonicalDoc(doc)

    private fun isLegacyDoc(doc: ObjectNode): Boolean = doc.hasNonNull("aggregationDefinition")

    private fun isSqlReport(doc: ObjectNode): Boolean =
        doc.hasNonNull("mode") && doc.get("mode").textValue() == "sql"

    private fun normalizeLegacyDoc(doc: ObjectNode): Report {
        val id = doc.get("_id").textValue()
        val title = doc.get("title")?.textValue() ?: ""
        val rawSql = doc.get("aggregationDefinition").textValue() ?: ""
        val neededArgs = doc.get("neededArgs")?.map { it.textValue() } ?: emptyList()

        return Report(
            id = id,
            title = title,
            items = listOf(ReportItem.ReportQuery(sql = rewriteLegacySql(rawSql, neededArgs))),
            transformations = buildTransformations(neededArgs)
        )
    }

    private fun buildTransformations(neededArgs: List<String>): Map<String, List<String>> {
        val result = mutableMapOf<String, List<String>>()
        if (neededArgs.any { it == "from" || it == "startDate" }) {
            result["startDate"] = listOf("SQL_FROM_DATE")
        }
        if (neededArgs.any { it == "to" || it == "endDate" }) {
            result["endDate"] = listOf("SQL_TO_DATE")
        }
        return result
    }

    private fun rewriteLegacySql(sql: String, neededArgs: List<String>): String {
        var result = sql
            .replace("\$from", "\$startDate")
            .replace("\$to", "\$endDate")
        for (arg in neededArgs) {
            val canonical = when (arg) {
                "from" -> "startDate"
                "to" -> "endDate"
                else -> arg
            }
            result = result.replaceFirst("?", "\$$canonical")
        }
        return result
    }

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

    private fun tryWriteMigratedDoc(legacyDoc: ObjectNode, canonicalReport: Report) {
        try {
            couchDbClient.putDatabaseDocument(
                database = REPORT_DATABASE,
                documentId = canonicalReport.id,
                body = buildMigratedDocBody(legacyDoc, canonicalReport)
            )
            logger.debug("Migrated ReportConfig {} to canonical form", canonicalReport.id)
        } catch (ex: Exception) {
            logger.warn(
                "Write-back migration failed for ReportConfig {}: {}",
                canonicalReport.id,
                ex.message
            )
        }
    }

    private fun buildMigratedDocBody(legacyDoc: ObjectNode, report: Report): Map<String, Any?> {
        val legacyOriginal =
            mapOf(
                "aggregationDefinition" to legacyDoc.get("aggregationDefinition")?.textValue(),
                "neededArgs" to legacyDoc.get("neededArgs")?.map { it.textValue() },
                "version" to legacyDoc.get("version")?.takeIf { !it.isNull && !it.isMissingNode }?.asText()
            )
        return mapOf(
            "_id" to report.id,
            "title" to report.title,
            "mode" to (legacyDoc.get("mode")?.textValue() ?: "sql"),
            "transformations" to report.transformations,
            "reportDefinition" to report.items.map { reportItemToMap(it) },
            "_legacyOriginal" to legacyOriginal
        )
    }

    private fun reportItemToMap(item: ReportItem): Map<String, Any?> =
        when (item) {
            is ReportItem.ReportQuery -> mapOf("query" to item.sql)
            is ReportItem.ReportGroup ->
                mapOf(
                    "groupTitle" to item.title,
                    "items" to item.items.map { reportItemToMap(it) }
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
