package com.aamdigital.aambackendservice.reporting.report.core

import com.aamdigital.aambackendservice.domain.DomainReference
import com.aamdigital.aambackendservice.reporting.domain.event.DocumentChangeEvent
import org.slf4j.LoggerFactory
import reactor.core.publisher.Mono

class DefaultIdentifyAffectedReportsUseCase(
    private val reportingStorage: ReportingStorage,
    private val reportSchemaGenerator: ReportSchemaGenerator,
) : IdentifyAffectedReportsUseCase {

    val logger = LoggerFactory.getLogger(javaClass)

    override fun analyse(documentChangeEvent: DocumentChangeEvent): Mono<List<DomainReference>> {

        val changedEntity = documentChangeEvent.documentId.split(":").first()

        return reportingStorage.fetchAllReports("sql")
            .map { reports ->
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
                affectedReports
            }
    }
}
