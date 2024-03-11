package com.aamdigital.aambackendservice.report.storage

import com.aamdigital.aambackendservice.couchdb.core.CouchDbStorage
import com.aamdigital.aambackendservice.couchdb.core.getQueryParamsAllDocs
import com.aamdigital.aambackendservice.report.dto.FetchReportsResponse
import com.aamdigital.aambackendservice.report.dto.ReportDoc
import org.springframework.stereotype.Service
import org.springframework.util.LinkedMultiValueMap
import reactor.core.publisher.Mono

@Service
class ReportRepository(
    private val couchDbStorage: CouchDbStorage,
) {
    companion object {
        private const val REPORT_DATABASE = "app"
    }

    fun fetchReports(): Mono<FetchReportsResponse> {
        return couchDbStorage.getDatabaseDocument(
            database = REPORT_DATABASE,
            documentId = "_all_docs",
            getQueryParamsAllDocs("ReportConfig"),
            FetchReportsResponse::class
        )
    }

    fun fetchReport(documentId: String, queryParams: LinkedMultiValueMap<String, String>): Mono<ReportDoc> {
        return couchDbStorage.getDatabaseDocument(
            database = REPORT_DATABASE,
            documentId = documentId,
            queryParams,
            ReportDoc::class
        )
    }
}
