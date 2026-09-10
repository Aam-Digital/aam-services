package com.aamdigital.aambackendservice.notification.queue

import com.aamdigital.aambackendservice.common.domain.UseCaseOutcome
import com.aamdigital.aambackendservice.common.queue.core.QueueMessage
import com.aamdigital.aambackendservice.notification.core.CreateUserNotificationEvent
import com.aamdigital.aambackendservice.notification.core.create.CreateNotificationRequest
import com.aamdigital.aambackendservice.notification.core.create.CreateNotificationUseCase
import com.aamdigital.aambackendservice.notification.core.outbox.NotificationOutboxEntry
import com.aamdigital.aambackendservice.notification.core.outbox.NotificationOutboxRepository
import com.aamdigital.aambackendservice.notification.domain.NotificationChannelType
import org.slf4j.LoggerFactory
import java.time.Clock
import java.util.UUID

/**
 * Records notifications that are owed to a user, replacing the `notification.user` queue.
 *
 * In-app notifications are delivered straight away: the write is a single CouchDB document, it is
 * idempotent because the notification id is derived from the document change that caused it, and it
 * is what the user actually reads - so there is no reason to make them wait for a drain tick. Push
 * and email are external calls with second-scale timeouts, so they go into the outbox and are
 * delivered by [com.aamdigital.aambackendservice.notification.core.outbox.NotificationOutboxDrainer]
 * off the caller's thread.
 *
 * If the immediate in-app delivery fails, the notification falls back to the outbox rather than
 * being dropped, so the retry policy covers every channel.
 *
 * [channel] is ignored; it survives only because the interface still carries the queue-shaped
 * signature that is retired together with `QueueMessage`.
 */
class OutboxUserNotificationPublisher(
    private val notificationOutboxRepository: NotificationOutboxRepository,
    private val createNotificationUseCase: CreateNotificationUseCase,
    private val clock: Clock = Clock.systemUTC()
) : UserNotificationPublisher {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun publish(
        channel: String,
        event: CreateUserNotificationEvent
    ): QueueMessage {
        if (event.notificationChannelType == NotificationChannelType.APP && deliverInApp(event)) {
            return queueMessage(event)
        }

        enqueue(event)
        return queueMessage(event)
    }

    /** @return true when the in-app notification was stored, false when it must be retried. */
    private fun deliverInApp(event: CreateUserNotificationEvent): Boolean =
        try {
            when (val outcome = createNotificationUseCase.run(CreateNotificationRequest(event))) {
                is UseCaseOutcome.Success -> true
                is UseCaseOutcome.Failure -> {
                    logger.warn(
                        "[{}] could not store in-app notification {} for user {}, moving it to the outbox: {}",
                        outcome.errorCode,
                        event.details.id,
                        event.userIdentifier,
                        outcome.errorMessage,
                        outcome.cause
                    )
                    false
                }
            }
        } catch (ex: Exception) {
            logger.warn(
                "Could not store in-app notification {} for user {}, moving it to the outbox: {}",
                event.details.id,
                event.userIdentifier,
                ex.message,
                ex
            )
            false
        }

    private fun enqueue(event: CreateUserNotificationEvent) {
        val now = clock.instant()

        notificationOutboxRepository.storeIfAbsent(
            NotificationOutboxEntry(
                id =
                    NotificationOutboxEntry.idFor(
                        notificationId = event.details.id.toString(),
                        channelType = event.notificationChannelType
                    ),
                userIdentifier = event.userIdentifier,
                notificationChannelType = event.notificationChannelType,
                notificationRule = event.notificationRule,
                details = event.details,
                nextAttemptAt = now,
                createdAt = now
            )
        )
    }

    private fun queueMessage(event: CreateUserNotificationEvent): QueueMessage =
        QueueMessage(
            id = UUID.randomUUID(),
            eventType = CreateUserNotificationEvent::class.java.canonicalName,
            event = event,
            createdAt = clock.instant().toString()
        )
}
