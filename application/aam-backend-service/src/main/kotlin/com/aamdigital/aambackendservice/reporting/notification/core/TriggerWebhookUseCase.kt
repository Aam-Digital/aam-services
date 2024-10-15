package com.aamdigital.aambackendservice.reporting.notification.core

import com.aamdigital.aambackendservice.reporting.domain.event.NotificationEvent

interface TriggerWebhookUseCase {
    fun trigger(notificationEvent: NotificationEvent)
}
