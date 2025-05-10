package com.aamdigital.aambackendservice.reporting.webhook.core

import com.aamdigital.aambackendservice.common.domain.DomainReference
import com.aamdigital.aambackendservice.reporting.webhook.WebhookEvent
import com.aamdigital.aambackendservice.reporting.webhook.di.ReportingNotificationQueueConfiguration
import com.aamdigital.aambackendservice.reporting.webhook.queue.WebhookEventPublisher
import com.aamdigital.aambackendservice.reporting.webhook.storage.WebhookStorage
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class NotificationService(
    private val webhookStorage: WebhookStorage,
    private val webhookEventPublisher: WebhookEventPublisher
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun getAffectedWebhooks(report: DomainReference): List<DomainReference> {
        val webhooks = webhookStorage.fetchAllWebhooks()

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
        webhookEventPublisher.publish(
            ReportingNotificationQueueConfiguration.NOTIFICATION_QUEUE,
            WebhookEvent(
                webhookId = webhook.id,
                reportId = report.id,
                calculationId = reportCalculation.id
            )
        )
    }
}
