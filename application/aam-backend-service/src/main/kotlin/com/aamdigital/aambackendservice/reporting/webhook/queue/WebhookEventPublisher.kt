package com.aamdigital.aambackendservice.reporting.webhook.queue

import com.aamdigital.aambackendservice.common.queue.core.QueueMessage
import com.aamdigital.aambackendservice.reporting.webhook.WebhookEvent

interface WebhookEventPublisher {
    fun publish(channel: String, event: WebhookEvent): QueueMessage
}
