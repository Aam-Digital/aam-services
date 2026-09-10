package com.aamdigital.aambackendservice.notification.core.outbox

import com.aamdigital.aambackendservice.common.domain.UseCaseOutcome
import com.aamdigital.aambackendservice.notification.core.CreateUserNotificationEvent
import com.aamdigital.aambackendservice.notification.core.create.CreateNotificationRequest
import com.aamdigital.aambackendservice.notification.core.create.CreateNotificationUseCase
import com.aamdigital.aambackendservice.notification.core.create.TransientNotificationException
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Delivers the notifications waiting in [NotificationOutboxRepository] and owns their retry policy.
 *
 * This replaces what the `notification.user` queue, its retry interceptor, its dead letter queue and
 * the restart-triggered dead letter drain did between them:
 *
 * - a transient failure (see [TransientNotificationException]) increments [NotificationOutboxEntry.attempts]
 *   and pushes [NotificationOutboxEntry.nextAttemptAt] out by an exponentially growing interval;
 * - once [maxAttempts] is reached the entry is *parked*: it keeps its `lastError` and stops being
 *   retried, which is what dead-lettering did, except the entry stays queryable
 *   (`GET notification-outbox/_all_docs?include_docs=true`) instead of sitting inside a broker;
 * - a permanent failure parks the entry immediately, as it did before;
 * - [resetParkedEntries] runs once per process and un-parks everything, reproducing the documented
 *   operator recovery path: fix the cause, restart the service, held notifications are retried.
 *
 * Delivery is idempotent for the in-app channel because notification ids are derived from the
 * document change that caused them, so a redelivery overwrites the same document. Push and email are
 * not idempotent, which is why a successful delivery deletes the entry before anything else can
 * observe it.
 */
class NotificationOutboxDrainer(
    private val notificationOutboxRepository: NotificationOutboxRepository,
    private val createNotificationUseCase: CreateNotificationUseCase,
    private val maxAttempts: Int,
    private val initialRetryInterval: Duration,
    private val maxRetryInterval: Duration,
    private val clock: Clock = Clock.systemUTC()
) {
    companion object {
        private const val RETRY_MULTIPLIER = 2
    }

    private val logger = LoggerFactory.getLogger(javaClass)
    private val parkedEntriesReset = AtomicBoolean(false)

    /** Delivers every entry whose next attempt is due. */
    fun drain() {
        val pending = notificationOutboxRepository.fetchPending()
        if (pending.isEmpty()) {
            return
        }

        resetParkedEntries(pending)

        val now = clock.instant()
        pending
            .filter { entry -> entry.attempts < maxAttempts && !entry.nextAttemptAt.isAfter(now) }
            .forEach { entry -> deliver(entry) }
    }

    /**
     * Un-parks entries that had run out of attempts, once per process.
     *
     * The equivalent used to be [com.aamdigital.aambackendservice.notification.queue] moving dead
     * lettered messages back onto the queue on startup. Doing it once per process rather than once
     * per tick is what stops a permanently failing notification from becoming a hot retry loop.
     */
    private fun resetParkedEntries(pending: List<NotificationOutboxEntry>) {
        if (!parkedEntriesReset.compareAndSet(false, true)) {
            return
        }

        val parked = pending.filter { entry -> entry.attempts >= maxAttempts }
        if (parked.isEmpty()) {
            return
        }

        logger.info(
            "Retrying {} notification(s) that had exhausted their delivery attempts before this restart",
            parked.size
        )

        parked.forEach { entry ->
            try {
                notificationOutboxRepository.store(
                    entry.copy(attempts = 0, nextAttemptAt = clock.instant())
                )
            } catch (ex: Exception) {
                logger.warn("Could not un-park notification {}: {}", entry.id, ex.message, ex)
            }
        }
    }

    private fun deliver(entry: NotificationOutboxEntry) {
        try {
            val outcome =
                createNotificationUseCase.run(
                    request =
                        CreateNotificationRequest(
                            createUserNotificationEvent =
                                CreateUserNotificationEvent(
                                    userIdentifier = entry.userIdentifier,
                                    notificationChannelType = entry.notificationChannelType,
                                    notificationRule = entry.notificationRule,
                                    details = entry.details
                                )
                        )
                )

            when (outcome) {
                is UseCaseOutcome.Success -> {
                    notificationOutboxRepository.delete(entry.id)
                    logger.debug(
                        "Delivered notification {} to user {} on channel {}",
                        entry.details.id,
                        entry.userIdentifier,
                        entry.notificationChannelType
                    )
                }

                is UseCaseOutcome.Failure -> {
                    park(entry, "[${outcome.errorCode}] ${outcome.errorMessage}", outcome.cause)
                }
            }
        } catch (ex: TransientNotificationException) {
            scheduleRetry(entry, ex)
        } catch (ex: Exception) {
            park(entry, ex.localizedMessage, ex)
        }
    }

    private fun scheduleRetry(
        entry: NotificationOutboxEntry,
        cause: Exception
    ) {
        val attempts = entry.attempts + 1

        if (attempts >= maxAttempts) {
            park(entry, cause.localizedMessage, cause)
            return
        }

        val delay = retryDelayAfter(attempts)
        logger.warn(
            "Transient failure delivering notification {} to user {} on channel {} " +
                "(attempt {} of {}), retrying in {}s: {}",
            entry.details.id,
            entry.userIdentifier,
            entry.notificationChannelType,
            attempts,
            maxAttempts,
            delay.toSeconds(),
            cause.message
        )

        store(
            entry.copy(
                attempts = attempts,
                nextAttemptAt = clock.instant().plus(delay),
                lastError = cause.localizedMessage
            )
        )
    }

    /**
     * Stops retrying the entry but keeps it, so the failure is visible and a restart can pick it up
     * again. This is the dead letter queue, expressed as a document.
     */
    private fun park(
        entry: NotificationOutboxEntry,
        reason: String?,
        cause: Throwable?
    ) {
        logger.error(
            "Giving up delivering notification {} to user {} on channel {} until the service restarts: {}",
            entry.details.id,
            entry.userIdentifier,
            entry.notificationChannelType,
            reason,
            cause
        )

        store(
            entry.copy(
                attempts = maxAttempts,
                nextAttemptAt = clock.instant(),
                lastError = reason
            )
        )
    }

    private fun store(entry: NotificationOutboxEntry) {
        try {
            notificationOutboxRepository.store(entry)
        } catch (ex: Exception) {
            // The entry keeps its previous state, so it is retried on a later tick rather than lost.
            logger.warn("Could not record delivery attempt for notification {}: {}", entry.id, ex.message, ex)
        }
    }

    private fun retryDelayAfter(attempts: Int): Duration {
        var delay = initialRetryInterval
        repeat(attempts - 1) {
            delay = delay.multipliedBy(RETRY_MULTIPLIER.toLong())
        }
        return if (delay > maxRetryInterval) maxRetryInterval else delay
    }
}
