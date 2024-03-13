package com.aamdigital.aambackendservice.reporting.domain.event

data class NotificationEvent(
    val webhookId: String,
    val reportId: String,
    val calculationId: String,
)
