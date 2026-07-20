package com.aamdigital.aambackendservice.reporting.report.storage

import com.aamdigital.aambackendservice.common.couchdb.core.CouchDbClient
import com.aamdigital.aambackendservice.reporting.report.Report
import com.aamdigital.aambackendservice.reporting.report.ReportItem
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.TextNode
import org.slf4j.LoggerFactory

/**
 * Transitional migration for the rename of the hard-wired SQS metadata columns
 * (created_at, created_by, updated_at, updated_by) to their "_" prefixed variants
 * (see SqsSchemaService.getDefaultEntityAttributes).
 *
 * Queries in stored ReportConfig documents are rewritten in memory on every read, so report
 * execution keeps working immediately after the schema rename. On single-report reads the
 * rewritten document is also written back to CouchDB, so the stored config (and what admins
 * see in the report editor) converges to the new column names.
 *
 * The rewrite is idempotent: the word-boundary regex does not match names that already carry
 * the "_" prefix (or any other word character around them, e.g. custom columns like
 * "my_created_at"). Occurrences inside string literals would be rewritten too; that is
 * acceptable because the old column names are only meaningful as column references.
 *
 * TODO: verify against production data whether any existing ReportConfig actually uses the old
 *  column names (created_at, created_by, updated_at, updated_by; check "created_by" especially,
 *  it was never documented). If no stored report uses them, this migration and its wiring in
 *  DefaultReportStorage can be removed and only the schema rename kept.
 */
class ReportColumnRenameMigration(
    private val couchDbClient: CouchDbClient
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val REPORT_DATABASE = "app"

        private val COLUMN_RENAMES =
            listOf(
                Regex("\\bcreated_at\\b") to "_created_at",
                Regex("\\bcreated_by\\b") to "_created_by",
                Regex("\\bupdated_at\\b") to "_updated_at",
                Regex("\\bupdated_by\\b") to "_updated_by"
            )
    }

    /**
     * Returns a copy of the report with all queries rewritten to the new column names.
     * Returns the same instance if nothing needed rewriting.
     */
    fun migrate(report: Report): Report {
        val migratedItems = report.items.map { migrateItem(it) }
        if (migratedItems == report.items) {
            return report
        }
        return report.copy(items = migratedItems)
    }

    /**
     * Persists the rewritten queries of a canonical ReportConfig document back to CouchDB,
     * keeping all other document fields untouched.
     */
    fun tryWriteRenamedDoc(rawDoc: ObjectNode) {
        val documentId = rawDoc.get("_id")?.textValue() ?: return
        rewriteQueryNodes(rawDoc.get("reportDefinition"))
        try {
            couchDbClient.putDatabaseDocument(
                database = REPORT_DATABASE,
                documentId = documentId,
                body = rawDoc
            )
            logger.info("Renamed deprecated SQS metadata columns in ReportConfig {}", documentId)
        } catch (ex: Exception) {
            logger.warn(
                "Write-back of renamed SQS metadata columns failed for ReportConfig {}: {}",
                documentId,
                ex.message
            )
        }
    }

    fun rewriteSql(sql: String): String =
        COLUMN_RENAMES.fold(sql) { result, (regex, replacement) ->
            regex.replace(result, replacement)
        }

    private fun migrateItem(item: ReportItem): ReportItem =
        when (item) {
            is ReportItem.ReportQuery -> {
                val rewritten = rewriteSql(item.sql)
                if (rewritten == item.sql) item else ReportItem.ReportQuery(sql = rewritten)
            }

            is ReportItem.ReportGroup -> {
                val migratedItems = item.items.map { migrateItem(it) }
                if (migratedItems == item.items) item else item.copy(items = migratedItems)
            }
        }

    private fun rewriteQueryNodes(node: JsonNode?) {
        if (node == null) {
            return
        }
        if (node.isArray) {
            node.forEach { rewriteQueryNodes(it) }
            return
        }
        if (node is ObjectNode) {
            val query = node.get("query")
            if (query != null && query.isTextual) {
                node.set<JsonNode>("query", TextNode(rewriteSql(query.textValue())))
            }
            rewriteQueryNodes(node.get("items"))
        }
    }
}
