package com.aamdigital.aambackendservice.common.execution

import org.slf4j.LoggerFactory
import org.springframework.core.NestedExceptionUtils
import java.time.Duration

/**
 * Retries a call a few times on the calling thread, waiting [initialInterval] after the first
 * failure and twice as long after each further one, then gives up and logs the root cause at ERROR.
 *
 * The wait blocks the calling thread, so this is only for a handful of short retries on a thread
 * that may wait. Work that has to survive a restart belongs in an
 * [com.aamdigital.aambackendservice.common.outbox.Outbox] instead. Spring Framework 7's
 * `RetryTemplate` can replace this once the service is on Spring Boot 4.
 *
 * @param attempts total number of calls, including the first
 */
class InlineRetry(
    private val attempts: Int,
    private val initialInterval: Duration
) {
    companion object {
        private const val MULTIPLIER = 2L
    }

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * @param description completes "Failed ..." and "Giving up ..." in the log messages
     * @return true when an attempt succeeded, false when all of them failed
     */
    fun run(
        description: String,
        action: () -> Unit
    ): Boolean {
        var interval = initialInterval

        for (attempt in 1..attempts) {
            try {
                action()
                return true
            } catch (ex: Exception) {
                if (attempt >= attempts) {
                    // ERROR so this is reported once to Sentry, grouped by its real cause
                    val rootCause = NestedExceptionUtils.getMostSpecificCause(ex)
                    logger.error(
                        "Giving up {} after {} attempts: {}",
                        description,
                        attempts,
                        rootCause.message,
                        rootCause
                    )
                    return false
                }

                logger.warn(
                    "Failed {} (attempt {} of {}), retrying in {}ms: {}",
                    description,
                    attempt,
                    attempts,
                    interval.toMillis(),
                    ex.localizedMessage
                )

                sleep(interval)
                interval = interval.multipliedBy(MULTIPLIER)
            }
        }

        return false
    }

    private fun sleep(interval: Duration) {
        if (interval.isZero || interval.isNegative) {
            return
        }

        try {
            Thread.sleep(interval.toMillis())
        } catch (ex: InterruptedException) {
            Thread.currentThread().interrupt()
            logger.debug("interrupted while waiting to retry", ex)
        }
    }
}
