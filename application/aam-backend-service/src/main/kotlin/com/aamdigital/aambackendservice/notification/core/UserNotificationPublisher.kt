package com.aamdigital.aambackendservice.notification.core

import com.aamdigital.aambackendservice.notification.core.event.CreateUserNotificationEvent
import com.aamdigital.aambackendservice.queue.core.QueueMessage

interface UserNotificationPublisher {
    fun publish(channel: String, event: CreateUserNotificationEvent): QueueMessage
}
