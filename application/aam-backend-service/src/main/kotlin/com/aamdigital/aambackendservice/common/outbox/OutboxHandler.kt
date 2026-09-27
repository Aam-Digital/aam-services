package com.aamdigital.aambackendservice.common.outbox

/**
 * Delivers one payload taken from an [Outbox]: the module's business logic, with no retry logic of
 * its own.
 *
 * Report every failure through the returned [OutboxDeliveryResult], because only the handler can
 * tell a failure worth retrying from one that will never succeed. An exception thrown here is
 * treated as [OutboxDeliveryResult.Rejected].
 */
fun interface OutboxHandler<P> {
    fun deliver(payload: P): OutboxDeliveryResult
}

sealed interface OutboxDeliveryResult {
    /** Done; the entry is deleted. */
    data object Delivered : OutboxDeliveryResult

    /** A transient failure; the entry is attempted again after a growing delay. */
    data class RetryLater(
        val reason: String?,
        val cause: Throwable? = null
    ) : OutboxDeliveryResult

    /** A failure retrying cannot fix; the entry is parked right away. */
    data class Rejected(
        val reason: String?,
        val cause: Throwable? = null
    ) : OutboxDeliveryResult
}
