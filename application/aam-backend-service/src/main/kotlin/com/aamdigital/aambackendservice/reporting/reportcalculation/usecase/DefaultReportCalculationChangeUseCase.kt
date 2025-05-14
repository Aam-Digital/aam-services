package com.aamdigital.aambackendservice.reporting.reportcalculation.usecase

import com.aamdigital.aambackendservice.common.changes.domain.DocumentChangeEvent
import com.aamdigital.aambackendservice.common.domain.DomainReference
import com.aamdigital.aambackendservice.reporting.reportcalculation.ReportCalculationStatus
import com.aamdigital.aambackendservice.reporting.reportcalculation.core.ReportCalculationChangeUseCase
import com.aamdigital.aambackendservice.reporting.reportcalculation.core.ReportCalculationStorage
import com.aamdigital.aambackendservice.reporting.reportcalculation.storage.ReportCalculationEntity
import com.aamdigital.aambackendservice.reporting.webhook.core.NotificationService
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
            objectMapper.convertValue(documentChangeEvent.currentVersion, ReportCalculationEntity::class.java)

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
                logger.debug("skipped notification for ${currentReportCalculation.report.id} ${currentReportCalculation.id} because data is unchanged")
            }
        } catch (ex: Exception) {
            logger.warn("Could not fetch ${currentReportCalculation.report.id} ${currentReportCalculation.id}. Skipped.", ex)
        }
    }
}
