package com.aamdigital.aambackendservice.reporting.report.usecase

import com.aamdigital.aambackendservice.common.changes.domain.DocumentChangeEvent
import com.aamdigital.aambackendservice.common.domain.DomainReference
import com.aamdigital.aambackendservice.reporting.report.core.IdentifyAffectedReportsUseCase
import com.aamdigital.aambackendservice.reporting.report.core.ReportQueryAnalyser
import com.aamdigital.aambackendservice.reporting.report.core.ReportStorage
import org.slf4j.LoggerFactory

class DefaultIdentifyAffectedReportsUseCase(
    private val reportStorage: ReportStorage,
    private val reportQueryAnalyser: ReportQueryAnalyser,
) : IdentifyAffectedReportsUseCase {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun analyse(documentChangeEvent: DocumentChangeEvent): List<DomainReference> {
        logger.trace("analyzing document change for auto report calculation {}", documentChangeEvent.documentId)

        val changedEntity = documentChangeEvent.documentId.split(":").first()


        // special handling if ReportConfig changed
        if (changedEntity == "ReportConfig") {
            if (documentChangeEvent.deleted) {
                logger.trace("Skipping ReportConfig delete event")
                return emptyList()
            }

            val reportRef = try {
                documentChangeEvent.currentVersion["_id"] as String
            } catch (ex: Exception) {
                logger.warn(ex.message, ex)
                return emptyList()
            }

            return mutableListOf( DomainReference(reportRef))
        }

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
