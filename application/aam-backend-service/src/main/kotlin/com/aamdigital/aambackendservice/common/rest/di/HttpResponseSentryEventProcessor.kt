package com.aamdigital.aambackendservice.common.rest.di

import io.sentry.EventProcessor
import io.sentry.Hint
import io.sentry.SentryEvent
import org.springframework.stereotype.Component

/**
 * Collapses the NullPointerExceptions that Tomcat throws from its own header table into one Sentry
 * issue.
 *
 * They surface while a response is committed or recycled - the streaming download endpoints reach
 * this code path from an async task, so the container can be tearing the request down while the
 * body is still being written. Tomcat's `MimeHeaders.headers[]` is then partly nulled and whichever
 * method touches it next fails.
 *
 * The exception message names that method and the loop variable of the frame that dereferenced the
 * entry, and the transaction is sometimes lost with the recycled request, so production produced
 * five separate issues (`MimeHeaderField.getName()`, `.recycle()`, `this.headers[i]`,
 * `this.headers[n]`, with and without a transaction) for a fault that always originates in the same
 * place. None of them individually accumulated enough events to escalate.
 *
 * Grouping on "an NPE thrown inside [MIME_HEADERS_CLASS]" keeps them together while staying
 * agnostic about the calling code, whose frames differ per endpoint and change as the streaming
 * helpers evolve. This is a grouping change only: the events keep their stack traces, `handled`
 * flag and level, so the underlying race stays visible - and now countable - rather than silenced.
 */
@Component
class HttpResponseSentryEventProcessor : EventProcessor {
    override fun process(
        event: SentryEvent,
        hint: Hint
    ): SentryEvent {
        if (isMimeHeadersFailure(event.throwable)) {
            event.fingerprints = listOf(MIME_HEADERS_FINGERPRINT)
        }

        return event
    }

    /**
     * The NPE is sometimes reported as the cause of a wrapper, so the whole chain is checked. The
     * depth bound guards against self-referential chains.
     */
    private fun isMimeHeadersFailure(throwable: Throwable?): Boolean {
        var cause = throwable
        var depth = 0

        while (cause != null && depth < MAX_CAUSE_DEPTH) {
            if (cause is NullPointerException &&
                cause.stackTrace.firstOrNull()?.className == MIME_HEADERS_CLASS
            ) {
                return true
            }

            cause = cause.cause
            depth += 1
        }

        return false
    }

    companion object {
        const val MIME_HEADERS_CLASS = "org.apache.tomcat.util.http.MimeHeaders"
        const val MIME_HEADERS_FINGERPRINT = "tomcat-mime-headers-recycled-during-response"

        private const val MAX_CAUSE_DEPTH = 10
    }
}
