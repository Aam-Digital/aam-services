package com.aamdigital.aambackendservice.reporting.notification.core

import com.aamdigital.aambackendservice.domain.DomainReference
import com.aamdigital.aambackendservice.reporting.domain.ReportCalculation
import com.aamdigital.aambackendservice.reporting.reportcalculation.core.CreateReportCalculationRequest
import com.aamdigital.aambackendservice.reporting.reportcalculation.core.CreateReportCalculationUseCase
import com.aamdigital.aambackendservice.reporting.reportcalculation.core.ReportCalculationStorage

class DefaultAddWebhookSubscriptionUseCase(
    private val notificationStorage: NotificationStorage,
    private val reportCalculationStorage: ReportCalculationStorage,
    private val notificationService: NotificationService,
    private val createReportCalculationUseCase: CreateReportCalculationUseCase
) : AddWebhookSubscriptionUseCase {
    override fun subscribe(report: DomainReference, webhook: DomainReference) {
        notificationStorage.addSubscription(
            webhookRef = webhook,
            entityRef = report
        )

        val reportCalculations = reportCalculationStorage.fetchReportCalculations(
            report = report
        )

        handleReportCalculations(reportCalculations, report, webhook)
    }

    private fun handleReportCalculations(
        calculations: List<ReportCalculation>,
        report: DomainReference,
        webhook: DomainReference
    ) {
        if (calculations.isEmpty()) {
            createReportCalculationUseCase.createReportCalculation(
                CreateReportCalculationRequest(
                    report = report,
                    args = mutableMapOf()
                )
            )
        } else {
            notificationService.triggerWebhook(
                report = report,
                webhook = webhook,
                reportCalculation = DomainReference(
                    calculations.sortedByDescending { it.calculationCompleted }.first().id
                )
            )
        }
    }
}
