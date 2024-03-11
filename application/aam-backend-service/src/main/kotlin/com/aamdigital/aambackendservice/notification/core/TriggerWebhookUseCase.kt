package com.aamdigital.aambackendservice.notification.core

import com.aamdigital.aambackendservice.notification.core.event.NotificationEvent
import reactor.core.publisher.Mono

interface TriggerWebhookUseCase {
    fun trigger(notificationEvent: NotificationEvent): Mono<Unit>
}
