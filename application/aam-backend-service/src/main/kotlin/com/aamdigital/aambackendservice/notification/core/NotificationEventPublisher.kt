package com.aamdigital.aambackendservice.notification.core

import com.aamdigital.aambackendservice.notification.core.event.NotificationEvent
import com.aamdigital.aambackendservice.queue.core.QueueMessage

interface NotificationEventPublisher {
    fun publish(channel: String, event: NotificationEvent): QueueMessage
}
