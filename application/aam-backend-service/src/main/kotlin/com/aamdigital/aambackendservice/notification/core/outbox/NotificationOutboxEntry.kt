package com.aamdigital.aambackendservice.notification.core.outbox

import com.aamdigital.aambackendservice.notification.domain.NotificationChannelType
import com.aamdigital.aambackendservice.notification.domain.NotificationDetails
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

/**
 * A notification that is owed to a user on one channel but has not been delivered yet.
 *
 * This is the durable record that used to be the `notification.user` queue message: it survives a
 * restart, carries its own retry state, and can be inspected with a plain CouchDB query when
 * deliveries start failing.
 *
 * The document id is derived from the notification id and the channel, so replaying the same
 * document change produces the same id and re-enqueueing is a no-op rather than a duplicate.
 */
data class NotificationOutboxEntry(
    @JsonProperty("_id")
    val id: String,
    val userIdentifier: String,
    val notificationChannelType: NotificationChannelType,
    val notificationRule: String,
    val details: NotificationDetails,
    /** Delivery attempts made so far. At or above the configured maximum the entry is parked. */
    val attempts: Int = 0,
    /** Earliest time the next delivery attempt may be made. */
    val nextAttemptAt: Instant,
    /** Why the last attempt failed, kept for the operator rather than for the retry logic. */
    val lastError: String? = null,
    val createdAt: Instant
) {
    companion object {
        const val ID_PREFIX = "NotificationOutbox"

        fun idFor(
            notificationId: String,
            channelType: NotificationChannelType
        ): String = "$ID_PREFIX:$notificationId:$channelType"
    }
}
