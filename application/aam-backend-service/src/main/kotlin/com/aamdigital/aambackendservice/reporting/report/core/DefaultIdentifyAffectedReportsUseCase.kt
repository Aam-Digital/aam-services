package com.aamdigital.aambackendservice.reporting.report.core

import com.aamdigital.aambackendservice.domain.DomainReference
import com.aamdigital.aambackendservice.reporting.domain.event.DocumentChangeEvent
import com.aamdigital.aambackendservice.reporting.storage.DefaultReportStorage

class DefaultIdentifyAffectedReportsUseCase(
    private val reportStorage: DefaultReportStorage,
    private val reportSchemaGenerator: ReportSchemaGenerator,
) : IdentifyAffectedReportsUseCase {

    override fun analyse(documentChangeEvent: DocumentChangeEvent): List<DomainReference> {

        val changedEntity = documentChangeEvent.documentId.split(":").first()

        val reports = reportStorage.fetchAllReports("sql")
        val affectedReports: MutableList<DomainReference> = mutableListOf()

        reports.forEach { report ->
            // todo better change detection (fields)
            val affectedEntities = reportSchemaGenerator.getAffectedEntities(report)
            val affected = affectedEntities.any {
                it == changedEntity
            }
            if (affected) {
                affectedReports.add(DomainReference(report.id))
            }
        }

        return affectedReports
    }
}
