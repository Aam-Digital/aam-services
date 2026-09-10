package com.aamdigital.aambackendservice.notification.core.outbox

import com.aamdigital.aambackendservice.notification.core.CreateUserNotificationEvent

/**
 * Records that a notification is owed to a user on one channel.
 *
 * See [OutboxUserNotificationPublisher] for how each channel is handled.
 */
interface UserNotificationPublisher {
    fun publish(event: CreateUserNotificationEvent)
}
