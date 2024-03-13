package com.aamdigital.aambackendservice.reporting.notification.core.event

data class NotificationEvent(
    val webhookId: String,
    val reportId: String,
    val calculationId: String,
)
