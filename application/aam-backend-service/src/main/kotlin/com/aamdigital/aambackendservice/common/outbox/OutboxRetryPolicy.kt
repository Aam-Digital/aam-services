package com.aamdigital.aambackendservice.common.outbox

import java.time.Duration

/**
 * How often and how far apart [OutboxDrainer] retries a transient failure before parking the entry.
 *
 * The delay doubles with every attempt, starting at [initialInterval] and capped at [maxInterval].
 */
data class OutboxRetryPolicy(
    val maxAttempts: Int,
    val initialInterval: Duration,
    val maxInterval: Duration
) {
    companion object {
        private const val MULTIPLIER = 2L
    }

    /** The delay before the next attempt, once [attempts] attempts have failed. */
    fun delayAfter(attempts: Int): Duration {
        var delay = initialInterval
        repeat(attempts - 1) {
            delay = delay.multipliedBy(MULTIPLIER)
        }
        return if (delay > maxInterval) maxInterval else delay
    }
}
