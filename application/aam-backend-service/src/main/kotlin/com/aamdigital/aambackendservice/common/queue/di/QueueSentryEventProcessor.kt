package com.aamdigital.aambackendservice.common.queue.di

import io.sentry.EventProcessor
import io.sentry.Hint
import io.sentry.SentryEvent
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

/**
 * Throttles the noisy, low-value RabbitMQ "Failed to check/redeclare auto-delete queue(s)." error that
 * Spring AMQP logs whenever a listener container races broker readiness on (re)connect. Without throttling a
 * single crash-looping instance can emit tens of thousands of identical events (see Sentry
 * AAM-BACKEND-SERVICE-22, which reached ~47k events from one deployment).
 *
 * At most one such event is forwarded to Sentry per [throttleWindow]; the rest are dropped. Keeping a periodic
 * sample means the condition stays visible if it ever recurs after the deployment-level fix, without flooding
 * the quota.
 */
@Component
class QueueSentryEventProcessor(
    @Value("\${aam.sentry.redeclare-queue-error.throttle-window:PT5M}")
    private val throttleWindow: Duration = Duration.ofMinutes(5),
) : EventProcessor {
    private val logger = LoggerFactory.getLogger(javaClass)

    // shared across consumer threads, so guard the "last forwarded" marker atomically
    private val lastForwarded = AtomicReference(Instant.MIN)

    override fun process(
        event: SentryEvent,
        hint: Hint,
    ): SentryEvent? {
        // Match the Sentry message model's actual fields. `Message.toString()` is NOT overridden, so the
        // previous `event.message?.toString()` check never matched and this processor was a silent no-op -
        // which is why all ~47k events reached Sentry. The logback integration always sets `formatted`.
        val message = event.message?.formatted ?: event.message?.message ?: event.throwable?.message
        if (message != THROTTLED_MESSAGE) {
            return event
        }

        val now = event.timestamp?.toInstant() ?: Instant.now()

        while (true) {
            val last = lastForwarded.get()
            if (Duration.between(last, now) < throttleWindow) {
                logger.debug("Throttling repeated '{}' event", THROTTLED_MESSAGE)
                return null
            }
            if (lastForwarded.compareAndSet(last, now)) {
                return event
            }
            // lost the race to another thread; retry with the updated marker
        }
    }

    companion object {
        private const val THROTTLED_MESSAGE = "Failed to check/redeclare auto-delete queue(s)."
    }
}
