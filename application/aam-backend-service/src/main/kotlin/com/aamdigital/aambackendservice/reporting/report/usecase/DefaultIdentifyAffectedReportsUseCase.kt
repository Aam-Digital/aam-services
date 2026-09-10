package com.aamdigital.aambackendservice.reporting.report.usecase

import com.aamdigital.aambackendservice.common.changes.DocumentChangeEvent
import com.aamdigital.aambackendservice.common.domain.DomainReference
import com.aamdigital.aambackendservice.reporting.report.core.IdentifyAffectedReportsUseCase
import com.aamdigital.aambackendservice.reporting.report.core.ReportConfigCache
import org.slf4j.LoggerFactory

class DefaultIdentifyAffectedReportsUseCase(
    private val reportConfigCache: ReportConfigCache
) : IdentifyAffectedReportsUseCase {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun analyse(documentChangeEvent: DocumentChangeEvent): List<DomainReference> {
        logger.trace("analyzing document change for auto report calculation {}", documentChangeEvent.documentId)

        val changedEntity = documentChangeEvent.documentId.split(":").first()

        // special handling if ReportConfig changed
        if (changedEntity == "ReportConfig") {
            // report definitions are cached, and this is the only event that can change them,
            // so an added, edited or deleted definition has to be picked up here
            reportConfigCache.markDirty()

            if (documentChangeEvent.deleted) {
                logger.trace("Skipping ReportConfig delete event")
                return emptyList()
            }

            val reportRef =
                try {
                    documentChangeEvent.currentVersion["_id"] as String
                } catch (ex: Exception) {
                    logger.warn(ex.message, ex)
                    return emptyList()
                }

            return mutableListOf(DomainReference(reportRef))
        }

        // todo better change detection (fields)
        return reportConfigCache.findReportsForEntityType(changedEntity)
    }
}
