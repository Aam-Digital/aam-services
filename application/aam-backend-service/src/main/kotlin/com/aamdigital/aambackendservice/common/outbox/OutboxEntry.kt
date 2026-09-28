package com.aamdigital.aambackendservice.common.outbox

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

/**
 * One unit of work in an [Outbox]: the [payload] a module wants delivered, plus the retry state
 * [Outbox] keeps for it.
 *
 * The retry fields sit next to the payload, not inside it, so an operator can see why something is
 * held (`attempts`, `lastError`) the same way for every outbox.
 */
data class OutboxEntry<P>(
    @JsonProperty("_id")
    val id: String,
    val payload: P,
    /** Delivery attempts made so far. At or above [OutboxRetryPolicy.maxAttempts] the entry is parked. */
    val attempts: Int = 0,
    /** Earliest time the next delivery attempt may be made. */
    val nextAttemptAt: Instant,
    /** Why the last attempt failed, kept for the operator rather than for the retry logic. */
    val lastError: String? = null,
    val createdAt: Instant
) {
    companion object {
        const val ID_PREFIX = "OutboxEntry"

        fun idFor(key: String): String = "$ID_PREFIX:$key"
    }
}
