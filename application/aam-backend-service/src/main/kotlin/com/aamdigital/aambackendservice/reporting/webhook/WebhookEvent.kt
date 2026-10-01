package com.aamdigital.aambackendservice.reporting.webhook

data class WebhookEvent(
    val webhookId: String,
    val reportId: String,
    val calculationId: String
)
