package com.aamdigital.aambackendservice.reporting.reportcalculation.core

import com.aamdigital.aambackendservice.domain.DomainReference
import com.aamdigital.aambackendservice.reporting.domain.ReportCalculation
import com.aamdigital.aambackendservice.reporting.domain.ReportCalculationStatus
import com.aamdigital.aambackendservice.reporting.domain.event.DocumentChangeEvent
import com.aamdigital.aambackendservice.reporting.notification.core.NotificationService
import com.aamdigital.aambackendservice.reporting.report.core.ReportingStorage
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import reactor.core.publisher.Mono

class DefaultReportCalculationChangeUseCase(
    private val reportingStorage: ReportingStorage,
    private val objectMapper: ObjectMapper,
    private val notificationService: NotificationService,
) : ReportCalculationChangeUseCase {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun handle(documentChangeEvent: DocumentChangeEvent): Mono<Unit> {

        val currentReportCalculation =
            objectMapper.convertValue(documentChangeEvent.currentVersion, ReportCalculation::class.java)

        if (currentReportCalculation.status != ReportCalculationStatus.FINISHED_SUCCESS) {
            return Mono.just(Unit)
        }

        return reportingStorage.fetchCalculations(
            reportReference = currentReportCalculation.report
        )
            .map { calculations ->
                calculations
                    .filter { it.id != currentReportCalculation.id }
                    .sortedBy { it.endDate }
            }
            .flatMap {
                val existingDigest = it.last().attachments["data.json"]?.digest
                val currentDigest = currentReportCalculation.attachments["data.json"]?.digest

                if (it.isEmpty()
                    || existingDigest != currentDigest
                ) {
                    notificationService.sendNotifications(
                        report = currentReportCalculation.report,
                        reportCalculation = DomainReference(currentReportCalculation.id)
                    )
                } else {
                    logger.debug("skipped notification for ${currentReportCalculation.id}")
                    Mono.just(Unit)
                }
            }
            .onErrorResume {
                logger.warn("Could not find ${currentReportCalculation.id}. Skipped.")
                Mono.just(Unit)
            }
    }
}
