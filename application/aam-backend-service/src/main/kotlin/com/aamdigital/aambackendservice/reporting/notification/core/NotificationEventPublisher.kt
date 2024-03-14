package com.aamdigital.aambackendservice.reporting.notification.core

import com.aamdigital.aambackendservice.queue.core.QueueMessage
import com.aamdigital.aambackendservice.reporting.domain.event.NotificationEvent

interface NotificationEventPublisher {
    fun publish(channel: String, event: NotificationEvent): QueueMessage
}
