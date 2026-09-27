package com.aamdigital.aambackendservice.common.scheduling

import org.slf4j.Logger

/**
 * Encapsulates exponential backoff state for scheduled jobs.
 *
 * The job's scheduled method keeps firing at its normal interval and passes its work to [run],
 * which skips it while backing off, and otherwise runs it and resets the backoff on success or
 * extends it on failure.
 */
class ScheduledJobBackoff(
    private val logger: Logger,
    private val jobLabel: String,
    internal var clock: () -> Long = System::currentTimeMillis,
) {
    companion object {
        const val INITIAL_BACKOFF_MS = 5_000L
        const val MAX_BACKOFF_MS = 86_400_000L // 24 hours

        fun calculateBackoffMs(attempt: Int): Long {
            val multiplier = 1L shl (attempt - 1).coerceAtMost(30)
            return (INITIAL_BACKOFF_MS * multiplier).coerceAtMost(MAX_BACKOFF_MS)
        }
    }

    private var errorCounter: Int = 0
    private var nextRetryAtMs: Long = 0

    fun run(action: () -> Unit) {
        if (nextRetryAtMs > 0 && clock() < nextRetryAtMs) return

        try {
            action()
            errorCounter = 0
            nextRetryAtMs = 0
        } catch (ex: Exception) {
            errorCounter += 1
            val backoffMs = calculateBackoffMs(errorCounter)
            nextRetryAtMs = clock() + backoffMs

            if (backoffMs >= MAX_BACKOFF_MS) {
                logger.error(
                    "[$jobLabel] An error occurred (count: {}). " +
                            "Max backoff reached, retrying in {} ms: {}",
                    errorCounter,
                    backoffMs,
                    ex.message
                )
            } else {
                logger.warn(
                    "[$jobLabel] An error occurred (count: {}). Retrying in {} ms: {}",
                    errorCounter,
                    backoffMs,
                    ex.message
                )
            }
            logger.debug("[$jobLabel] Debug information", ex)
        }
    }
}
