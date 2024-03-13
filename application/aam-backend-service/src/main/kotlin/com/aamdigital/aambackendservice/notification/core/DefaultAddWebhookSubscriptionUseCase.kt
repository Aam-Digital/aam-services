package com.aamdigital.aambackendservice.notification.core

import com.aamdigital.aambackendservice.domain.DomainReference
import com.aamdigital.aambackendservice.report.calculation.core.CreateReportCalculationRequest
import com.aamdigital.aambackendservice.report.calculation.core.CreateReportCalculationUseCase
import com.aamdigital.aambackendservice.report.core.ReportingStorage
import reactor.core.publisher.Mono

class DefaultAddWebhookSubscriptionUseCase(
    private val notificationStorage: NotificationStorage,
    private val reportingStorage: ReportingStorage,
    private val notificationService: NotificationService,
    private val createReportCalculationUseCase: CreateReportCalculationUseCase
) : AddWebhookSubscriptionUseCase {
    override fun subscribe(report: DomainReference, webhook: DomainReference): Mono<Unit> {
        return notificationStorage.addSubscription(
            webhookRef = webhook,
            entityRef = report
        ).flatMap {
            reportingStorage.fetchCalculations(
                reportReference = report
            ).flatMap { calculations ->
                if (calculations.isEmpty()) {
                    createReportCalculationUseCase.startReportCalculation(
                        CreateReportCalculationRequest(
                            report = report,
                        )
                    ).flatMap {
                        Mono.just(Unit)
                    }
                } else {
                    notificationService.triggerWebhook(
                        report = report,
                        webhook = webhook,
                        reportCalculation = DomainReference(
                            calculations.sortedBy { it.endDate }.first().id
                        )
                    )
                    Mono.just(Unit)
                }
            }
        }

    }
}
