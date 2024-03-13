package com.aamdigital.aambackendservice.reporting.notification.core

import com.aamdigital.aambackendservice.reporting.notification.core.event.NotificationEvent
import reactor.core.publisher.Mono

interface TriggerWebhookUseCase {
    fun trigger(notificationEvent: NotificationEvent): Mono<Unit>
}
