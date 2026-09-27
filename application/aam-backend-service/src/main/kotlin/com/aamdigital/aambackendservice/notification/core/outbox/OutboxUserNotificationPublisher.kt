package com.aamdigital.aambackendservice.notification.core.outbox

import com.aamdigital.aambackendservice.common.outbox.Outbox
import com.aamdigital.aambackendservice.common.outbox.OutboxHandler
import com.aamdigital.aambackendservice.notification.core.CreateUserNotificationEvent
import com.aamdigital.aambackendservice.notification.domain.NotificationChannelType

/**
 * Records notifications that are owed to a user in the notification [Outbox].
 *
 * In-app notifications are delivered straight away: the write is a single CouchDB document, it is
 * idempotent because the notification id is derived from the document change that caused it, and it
 * is what the user actually reads - so there is no reason to make them wait for a drain tick. If
 * that write fails, the notification goes to the outbox rather than being dropped. Push and email
 * are external calls with second-scale timeouts, so they always go to the outbox and are delivered
 * off the caller's thread.
 */
class OutboxUserNotificationPublisher(
    private val notificationOutbox: Outbox<CreateUserNotificationEvent>,
    private val notificationOutboxHandler: OutboxHandler<CreateUserNotificationEvent>
) : UserNotificationPublisher {
    override fun publish(event: CreateUserNotificationEvent) {
        // the notification id is derived from the document change, so a replayed change re-derives this key
        val key = "${event.details.id}:${event.notificationChannelType}"

        if (event.notificationChannelType == NotificationChannelType.APP) {
            notificationOutbox.deliverNowOrEnqueue(key, event, notificationOutboxHandler)
        } else {
            notificationOutbox.enqueue(key, event)
        }
    }
}
