package com.aamdigital.aambackendservice.reporting.webhook

import com.aamdigital.aambackendservice.common.events.DomainEvent

data class WebhookEvent(
    val webhookId: String,
    val reportId: String,
    val calculationId: String,
) : DomainEvent()
