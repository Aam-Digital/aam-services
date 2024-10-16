package com.aamdigital.aambackendservice.reporting.notification.core

import com.aamdigital.aambackendservice.domain.DomainReference
import com.aamdigital.aambackendservice.reporting.domain.event.NotificationEvent
import com.aamdigital.aambackendservice.reporting.notification.di.NotificationQueueConfiguration
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class NotificationService(
    private val notificationStorage: NotificationStorage,
    private val notificationEventPublisher: NotificationEventPublisher
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun getAffectedWebhooks(report: DomainReference): List<DomainReference> {
        val webhooks = notificationStorage.fetchAllWebhooks()

        val affectedWebhooks: MutableList<DomainReference> = mutableListOf()
        webhooks.forEach { webhook ->
            if (webhook.reportSubscriptions.contains(report)) {
                affectedWebhooks.add(DomainReference(webhook.id))
            }
        }

        return affectedWebhooks
    }

    fun sendNotifications(report: DomainReference, reportCalculation: DomainReference) {
        logger.debug("[NotificationService]: Trigger all affected webhooks for ${report.id}")
        val affectedWebhooks = getAffectedWebhooks(report)
        
        affectedWebhooks.map { webhook ->
            triggerWebhook(
                report = report,
                reportCalculation = reportCalculation,
                webhook = webhook
            )
        }
    }

    fun triggerWebhook(report: DomainReference, reportCalculation: DomainReference, webhook: DomainReference) {
        logger.debug("[NotificationService]: Trigger NotificationEvent for ${webhook.id} and ${report.id}")
        notificationEventPublisher.publish(
            NotificationQueueConfiguration.NOTIFICATION_QUEUE,
            NotificationEvent(
                webhookId = webhook.id,
                reportId = report.id,
                calculationId = reportCalculation.id
            )
        )
    }
}
