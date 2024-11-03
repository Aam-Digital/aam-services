package com.aamdigital.aambackendservice.reporting.domain.event

import com.aamdigital.aambackendservice.events.DomainEvent

data class NotificationEvent(
    val webhookId: String,
    val reportId: String,
    val calculationId: String,
) : DomainEvent()
