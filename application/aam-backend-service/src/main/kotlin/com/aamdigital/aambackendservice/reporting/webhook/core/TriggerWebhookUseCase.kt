package com.aamdigital.aambackendservice.reporting.webhook.core

import com.aamdigital.aambackendservice.reporting.webhook.WebhookEvent

interface TriggerWebhookUseCase {
    fun trigger(webhookEvent: WebhookEvent)
}
