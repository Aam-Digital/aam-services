package com.aamdigital.aambackendservice.notification.core.event

data class NotificationEvent(
    val webhookId: String,
    val reportId: String,
    val calculationId: String,
)
