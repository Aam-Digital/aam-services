package com.aamdigital.aambackendservice.reporting.report.storage

import com.aamdigital.aambackendservice.common.couchdb.core.CouchDbClient
import com.aamdigital.aambackendservice.reporting.report.Report
import com.aamdigital.aambackendservice.reporting.report.ReportItem
import com.fasterxml.jackson.databind.node.ObjectNode
import org.slf4j.LoggerFactory

/**
 * Transitional migration for legacy v1 ReportConfig documents.
 *
 * Detects v1 documents (aggregationDefinition + neededArgs + positional/named placeholders),
 * normalizes them to the canonical [Report] form, and writes the migrated document back to
 * CouchDB on first read so the migration is persisted.
 *
 * This whole file is throwaway: once every production ReportConfig document has been read
 * (and thus migrated) at least once, delete this class together with its two usages in
 * [DefaultReportStorage] (the legacy branch of normalizeRawDocToReport and the write-back in
 * fetchReport).
 */
class ReportConfigLegacyMigration(
    private val couchDbClient: CouchDbClient
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val REPORT_DATABASE = "app"
    }

    fun isLegacyDoc(doc: ObjectNode): Boolean = doc.hasNonNull("aggregationDefinition")

    fun normalizeLegacyDoc(doc: ObjectNode): Report {
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

    fun tryWriteMigratedDoc(
        legacyDoc: ObjectNode,
        canonicalReport: Report
    ) {
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

    private fun rewriteLegacySql(
        sql: String,
        neededArgs: List<String>
    ): String {
        var result =
            sql
                .replace("\$from", "\$startDate")
                .replace("\$to", "\$endDate")
        for (arg in neededArgs) {
            val canonical =
                when (arg) {
                    "from" -> "startDate"
                    "to" -> "endDate"
                    else -> arg
                }
            result = result.replaceFirst("?", "\$$canonical")
        }
        return result
    }

    private fun buildMigratedDocBody(
        legacyDoc: ObjectNode,
        report: Report
    ): Map<String, Any?> {
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
            // not "_legacyOriginal": CouchDB rejects unknown underscore-prefixed
            // top-level members with a doc_validation error.
            "legacyOriginal" to legacyOriginal
        )
    }

    private fun reportItemToMap(item: ReportItem): Map<String, Any?> =
        when (item) {
            is ReportItem.ReportQuery -> {
                mapOf("query" to item.sql)
            }

            is ReportItem.ReportGroup -> {
                mapOf(
                    "groupTitle" to item.title,
                    "items" to item.items.map { reportItemToMap(it) }
                )
            }
        }
}
