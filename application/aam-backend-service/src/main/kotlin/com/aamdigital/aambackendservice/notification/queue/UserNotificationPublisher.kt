package com.aamdigital.aambackendservice.notification.queue

import com.aamdigital.aambackendservice.notification.core.CreateUserNotificationEvent
import com.aamdigital.aambackendservice.queue.core.QueueMessage

interface UserNotificationPublisher {
    fun publish(channel: String, event: CreateUserNotificationEvent): QueueMessage
}
