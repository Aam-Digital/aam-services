package com.aamdigital.aambackendservice.reporting.report.usecase

import com.aamdigital.aambackendservice.common.changes.domain.DocumentChangeEvent
import com.aamdigital.aambackendservice.common.domain.DomainReference
import com.aamdigital.aambackendservice.reporting.report.core.IdentifyAffectedReportsUseCase
import com.aamdigital.aambackendservice.reporting.report.core.ReportQueryAnalyser
import com.aamdigital.aambackendservice.reporting.report.core.ReportStorage

class DefaultIdentifyAffectedReportsUseCase(
    private val reportStorage: ReportStorage,
    private val reportQueryAnalyser: ReportQueryAnalyser,
) : IdentifyAffectedReportsUseCase {

    override fun analyse(documentChangeEvent: DocumentChangeEvent): List<DomainReference> {

        val changedEntity = documentChangeEvent.documentId.split(":").first()

        val reports = reportStorage.fetchAllReports("sql")
        val affectedReports: MutableList<DomainReference> = mutableListOf()

        reports.forEach { report ->
            // todo better change detection (fields)
            val affectedEntities = reportQueryAnalyser.getAffectedEntities(report)
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
