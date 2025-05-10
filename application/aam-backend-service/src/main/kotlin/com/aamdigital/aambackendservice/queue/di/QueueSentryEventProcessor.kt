package com.aamdigital.aambackendservice.queue.di

import io.sentry.EventProcessor
import io.sentry.Hint
import io.sentry.Sentry
import io.sentry.SentryEvent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.*

@Component
class QueueSentryEventProcessor : EventProcessor {
    private val logger = LoggerFactory.getLogger(javaClass)

    init {
        logger.info("SENTRY: QueueSentryEventProcessor initialized " + Sentry.isEnabled())
    }

    private var lastFailedCheckQueuesError: Date? = null;
    override fun process(event: SentryEvent, hint: Hint): SentryEvent? {
        logger.trace("SENTRY EVENT: ${event.throwable?.javaClass?.toString()} ${event.message?.toString()}")

        if (event.message?.toString() == "Failed to check/redeclare auto-delete queue(s).") {
            if (lastFailedCheckQueuesError == null) {
                lastFailedCheckQueuesError = event.timestamp
                logger.info("SENTRY: QueueSentryEventProcessor: ignoring first Queue error")
                return null
            }
        }

        return event
    }
}
