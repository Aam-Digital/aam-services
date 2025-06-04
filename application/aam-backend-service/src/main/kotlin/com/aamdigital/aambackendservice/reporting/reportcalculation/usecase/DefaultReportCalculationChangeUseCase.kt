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
                .filter {
                    it.id != currentReportCalculation.id // don't compare with itself
                            && it.report.id == currentReportCalculation.report.id
                            && it.status == ReportCalculationStatus.FINISHED_SUCCESS
                }
                .sortedBy { it.calculationCompleted }
                .lastOrNull()
                ?.attachments?.get("data.json")?.digest

            val currentDigest = currentReportCalculation.attachments["data.json"]?.digest

            if (existingDigest != currentDigest) {
                notificationService.sendNotifications(
                    report = currentReportCalculation.report,
                    reportCalculation = DomainReference(currentReportCalculation.id)
                )
            } else {
                logger.debug("skipped notification for {} {} because data is unchanged", currentReportCalculation.report.id, currentReportCalculation.id)

                if (currentReportCalculation.fromAutomaticChangeDetection == true) {
                    logger.debug("deleting automatically created report-calculation that is just duplicating existing result {}", currentReportCalculation.id)
                    reportCalculationStorage.deleteReportCalculation(
                        DomainReference(currentReportCalculation.id)
                    )
                }
            }
        } catch (ex: Exception) {
            logger.warn("Could not fetch {} {}. Skipped.", currentReportCalculation.report.id, currentReportCalculation.id, ex)
        }
    }
}
