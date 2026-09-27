package com.aamdigital.aambackendservice.common.outbox

import org.slf4j.LoggerFactory
import java.time.Clock
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Delivers the entries waiting in an [Outbox] through its [OutboxHandler], and owns their retry
 * policy so the handler does not need one:
 *
 * - [OutboxDeliveryResult.Delivered] deletes the entry, before anything else can observe it, so a
 *   handler that is not idempotent (sending an email, say) is not called again for it;
 * - [OutboxDeliveryResult.RetryLater] increments [OutboxEntry.attempts] and pushes
 *   [OutboxEntry.nextAttemptAt] out as [OutboxRetryPolicy.delayAfter] says;
 * - once [OutboxRetryPolicy.maxAttempts] is reached the entry is *parked*: it keeps its `lastError`
 *   and stops being retried, like a dead letter queue, except the entry stays queryable
 *   (`GET <database>/_all_docs?include_docs=true`) instead of sitting inside a broker;
 * - [OutboxDeliveryResult.Rejected], or an exception from the handler, parks the entry immediately;
 * - [resetParkedEntries] runs once per process and un-parks everything, which makes the operator's
 *   recovery path: fix the cause, restart the service, and held entries are retried.
 *
 * [drain] is meant to be called from a `@Scheduled` job wrapped in `ScheduledJobBackoff`.
 */
class OutboxDrainer<P : Any>(
    private val outbox: Outbox<P>,
    private val handler: OutboxHandler<P>,
    private val retryPolicy: OutboxRetryPolicy,
    private val clock: Clock = Clock.systemUTC()
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val parkedEntriesReset = AtomicBoolean(false)
    private val maxAttempts = retryPolicy.maxAttempts

    /** Delivers every entry whose next attempt is due. */
    fun drain() {
        val pending = outbox.fetchAll()
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
     * Doing it once per process rather than once per tick is what stops a permanently failing entry
     * from becoming a hot retry loop.
     */
    private fun resetParkedEntries(pending: List<OutboxEntry<P>>) {
        if (!parkedEntriesReset.compareAndSet(false, true)) {
            return
        }

        val parked = pending.filter { entry -> entry.attempts >= maxAttempts }
        if (parked.isEmpty()) {
            return
        }

        logger.info(
            "Retrying {} {} entries that had exhausted their delivery attempts before this restart",
            parked.size,
            outbox.database
        )

        parked.forEach { entry ->
            try {
                outbox.store(entry.copy(attempts = 0, nextAttemptAt = clock.instant()))
            } catch (ex: Exception) {
                logger.warn("Could not un-park {} entry {}: {}", outbox.database, entry.id, ex.message, ex)
            }
        }
    }

    private fun deliver(entry: OutboxEntry<P>) {
        try {
            when (val result = handler.deliver(entry.payload)) {
                is OutboxDeliveryResult.Delivered -> {
                    outbox.delete(entry.id)
                    logger.debug("Delivered {} entry {}", outbox.database, entry.id)
                }

                is OutboxDeliveryResult.RetryLater -> {
                    scheduleRetry(entry, result.reason, result.cause)
                }

                is OutboxDeliveryResult.Rejected -> {
                    park(entry, result.reason, result.cause)
                }
            }
        } catch (ex: Exception) {
            park(entry, ex.localizedMessage, ex)
        }
    }

    private fun scheduleRetry(
        entry: OutboxEntry<P>,
        reason: String?,
        cause: Throwable?
    ) {
        val attempts = entry.attempts + 1

        if (attempts >= maxAttempts) {
            park(entry, reason, cause)
            return
        }

        val delay = retryPolicy.delayAfter(attempts)
        logger.warn(
            "Transient failure delivering {} entry {} (attempt {} of {}), retrying in {}s: {}",
            outbox.database,
            entry.id,
            attempts,
            maxAttempts,
            delay.toSeconds(),
            reason
        )

        store(
            entry.copy(
                attempts = attempts,
                nextAttemptAt = clock.instant().plus(delay),
                lastError = reason
            )
        )
    }

    /**
     * Stops retrying the entry but keeps it, so the failure is visible and a restart can pick it up
     * again. This is the dead letter queue, expressed as a document.
     */
    private fun park(
        entry: OutboxEntry<P>,
        reason: String?,
        cause: Throwable?
    ) {
        logger.error(
            "Giving up delivering {} entry {} until the service restarts: {}",
            outbox.database,
            entry.id,
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

    private fun store(entry: OutboxEntry<P>) {
        try {
            outbox.store(entry)
        } catch (ex: Exception) {
            // The entry keeps its previous state, so it is retried on a later tick rather than lost.
            logger.warn(
                "Could not record delivery attempt for {} entry {}: {}",
                outbox.database,
                entry.id,
                ex.message,
                ex
            )
        }
    }
}
