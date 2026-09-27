package com.aamdigital.aambackendservice.reporting.webhook.core

import com.aamdigital.aambackendservice.common.domain.DomainReference
import com.aamdigital.aambackendservice.common.execution.BoundedTaskRunner
import com.aamdigital.aambackendservice.reporting.webhook.WebhookEvent
import com.aamdigital.aambackendservice.reporting.webhook.storage.WebhookStorage
import org.slf4j.LoggerFactory

/**
 * Calls the webhooks that subscribed to a report once a calculation produced a new result.
 *
 * Delivery is handed to [webhookDeliveryRunner] rather than performed inline, so that outbound HTTP
 * to a slow or unreachable subscriber cannot hold up the report calculation that produced the
 * result, nor the HTTP request that registers a subscription. The runner logs a failed callback at
 * ERROR.
 *
 * Delivery stays fire-and-forget and is *not* retried: a failed callback is logged and dropped.
 * That is what the `notification.webhook` queue did too - its consumer rejected every failure onto
 * a dead letter queue that nothing drains. Adding retry needs the ordering question answered first
 * ("do not send an old event after a newer calculation already delivered") and is out of scope.
 */
class NotificationService(
    private val webhookStorage: WebhookStorage,
    private val triggerWebhookUseCase: TriggerWebhookUseCase,
    private val webhookDeliveryRunner: BoundedTaskRunner
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

    fun sendNotifications(
        report: DomainReference,
        reportCalculation: DomainReference
    ) {
        logger.debug("[NotificationService]: Trigger all affected webhooks for ${report.id}")
        val affectedWebhooks = getAffectedWebhooks(report)

        affectedWebhooks.forEach { webhook ->
            triggerWebhook(
                report = report,
                reportCalculation = reportCalculation,
                webhook = webhook
            )
        }
    }

    fun triggerWebhook(
        report: DomainReference,
        reportCalculation: DomainReference,
        webhook: DomainReference
    ) {
        logger.debug("[NotificationService]: Trigger NotificationEvent for ${webhook.id} and ${report.id}")

        val webhookEvent =
            WebhookEvent(
                webhookId = webhook.id,
                reportId = report.id,
                calculationId = reportCalculation.id
            )

        val accepted =
            webhookDeliveryRunner.submit(
                "webhook ${webhookEvent.webhookId} (report ${webhookEvent.reportId}, " +
                    "calculation ${webhookEvent.calculationId})"
            ) {
                triggerWebhookUseCase.trigger(webhookEvent)
            }

        if (!accepted) {
            // The backlog is bounded on purpose: rejecting keeps the calculation thread moving,
            // which is the whole reason this hop exists. Dropping is the same outcome the
            // write-only dead letter queue had, so log at ERROR to reach Sentry.
            logger.error(
                "Dropped webhook callback for webhook {} (report {}, calculation {}): " +
                    "the webhook delivery executor is saturated or shutting down",
                webhookEvent.webhookId,
                webhookEvent.reportId,
                webhookEvent.calculationId
            )
        }
    }
}
