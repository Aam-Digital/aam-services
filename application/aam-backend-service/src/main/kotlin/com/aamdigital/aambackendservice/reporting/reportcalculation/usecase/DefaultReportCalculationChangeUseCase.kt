package com.aamdigital.aambackendservice.reporting.reportcalculation.usecase

import com.aamdigital.aambackendservice.domain.DomainReference
import com.aamdigital.aambackendservice.reporting.domain.ReportCalculation
import com.aamdigital.aambackendservice.reporting.domain.ReportCalculationStatus
import com.aamdigital.aambackendservice.reporting.domain.event.DocumentChangeEvent
import com.aamdigital.aambackendservice.reporting.notification.core.NotificationService
import com.aamdigital.aambackendservice.reporting.reportcalculation.core.ReportCalculationChangeUseCase
import com.aamdigital.aambackendservice.reporting.reportcalculation.core.ReportCalculationStorage
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory

class DefaultReportCalculationChangeUseCase(
    private val reportCalculationStorage: ReportCalculationStorage,
    private val objectMapper: ObjectMapper,
    private val notificationService: NotificationService,
) : ReportCalculationChangeUseCase {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun handle(documentChangeEvent: DocumentChangeEvent) {
        val currentReportCalculation =
            objectMapper.convertValue(documentChangeEvent.currentVersion, ReportCalculation::class.java)

        if (currentReportCalculation.status != ReportCalculationStatus.FINISHED_SUCCESS) {
            return
        }

        try {
            val calculations = reportCalculationStorage.fetchReportCalculations(
                report = currentReportCalculation.report
            )

            val existingDigest = calculations
                .filter { it.id != currentReportCalculation.id }
                .sortedBy { it.calculationCompleted }
                .lastOrNull()?.attachments?.get("data.json")?.digest

            val currentDigest = currentReportCalculation.attachments["data.json"]?.digest

            if (existingDigest != currentDigest
            ) {
                notificationService.sendNotifications(
                    report = currentReportCalculation.report,
                    reportCalculation = DomainReference(currentReportCalculation.id)
                )
            } else {
                logger.debug("skipped notification for ${currentReportCalculation.id}")
            }
        } catch (ex: Exception) {
            logger.warn("Could not fetch ${currentReportCalculation.id}. Skipped.", ex)
        }
    }
}
