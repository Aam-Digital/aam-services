package com.aamdigital.aambackendservice.reporting.storage

import com.aamdigital.aambackendservice.couchdb.core.CouchDbClient
import com.aamdigital.aambackendservice.couchdb.core.getQueryParamsAllDocs
import com.aamdigital.aambackendservice.domain.DomainReference
import com.aamdigital.aambackendservice.reporting.domain.Report
import com.aamdigital.aambackendservice.reporting.domain.ReportSchema
import com.aamdigital.aambackendservice.reporting.report.core.ReportSchemaGenerator
import com.aamdigital.aambackendservice.reporting.report.dto.FetchReportsResponse
import com.aamdigital.aambackendservice.reporting.report.dto.ReportDoc
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.util.LinkedMultiValueMap
import java.util.*

@Service
// todo add ReportStorage interface
class DefaultReportStorage(
    private val couchDbClient: CouchDbClient,
    private val reportSchemaGenerator: ReportSchemaGenerator,
) {
    companion object {
        private const val REPORT_DATABASE = "app"
    }

    private val logger = LoggerFactory.getLogger(javaClass)

    fun fetchAllReports(mode: String): List<Report> {
        val response = couchDbClient.getDatabaseDocument(
            database = REPORT_DATABASE,
            documentId = "_all_docs",
            getQueryParamsAllDocs("ReportConfig"),
            FetchReportsResponse::class
        )

        if (response.rows.isEmpty()) {
            return emptyList()
        }

        return response.rows.filter {
            it.doc.mode == mode
        }.map {
            Report(
                id = it.id,
                name = it.doc.title,
                mode = it.doc.mode,
                query = it.doc.aggregationDefinition ?: "",
                schema = ReportSchema(
                    fields = reportSchemaGenerator.getTableNamesByQuery(it.doc.aggregationDefinition ?: "")
                ),
                neededArgs = it.doc.neededArgs
            )
        }
    }

    fun fetchReport(report: DomainReference): Optional<Report> {
        val reportDoc = try {
            couchDbClient.getDatabaseDocument(
                database = REPORT_DATABASE,
                documentId = report.id,
                queryParams = LinkedMultiValueMap(),
                kClass = ReportDoc::class
            )
        } catch (ex: Exception) {
            logger.warn("[DefaultReportStorage] Could not fetch DatabaseDocument: ${report.id}", ex)
            return Optional.empty()
        }

        return Optional.of(
            Report(
                id = reportDoc.id,
                name = reportDoc.title,
                query = reportDoc.aggregationDefinition ?: "",
                mode = reportDoc.mode,
                schema = ReportSchema(
                    fields = reportSchemaGenerator.getTableNamesByQuery(reportDoc.aggregationDefinition ?: "")
                ),
                neededArgs = reportDoc.neededArgs
            )
        )
    }
}
