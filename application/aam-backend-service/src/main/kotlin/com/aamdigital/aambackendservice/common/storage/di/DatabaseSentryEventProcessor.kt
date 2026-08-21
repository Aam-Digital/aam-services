package com.aamdigital.aambackendservice.common.storage.di

import io.sentry.EventProcessor
import io.sentry.Hint
import io.sentry.SentryEvent
import org.hibernate.exception.JDBCConnectionException
import org.springframework.jdbc.CannotGetJdbcConnectionException
import org.springframework.stereotype.Component
import java.sql.SQLException

/**
 * Gives database errors a Sentry grouping key that stays stable across occurrences.
 *
 * Hibernate logs SQL failures at ERROR through [SQL_EXCEPTION_LOGGER] *before* the exception
 * propagates, so they become Sentry events even though the only code path that reaches them -
 * the scheduled jobs wrapped in
 * [com.aamdigital.aambackendservice.common.scheduling.ScheduledJobBackoff] - already catches,
 * backs off and retries. The service therefore recovers on its own; what it does not do is
 * report the failure as one recurring fault.
 *
 * A single episode of "Postgres is unreachable" reaches Sentry through several channels that all
 * describe the same fault:
 *  - the driver's own wording, which differs per failure mode ("An I/O error occurred while
 *    sending to the backend.", "This connection has been closed.", "The connection attempt
 *    failed."),
 *  - HikariCP's pool timeout, which interpolates the elapsed wait and the pool counts
 *    ("... request timed out after 37386ms (total=7, active=0, idle=6, waiting=0)"),
 *  - the translated [JDBCConnectionException] that Spring's transaction interceptor logs with the
 *    failing statement embedded in the message.
 *
 * Each wording became its own Sentry issue, so one outage arrived as five unrelated-looking
 * issues, and the per-issue event counts that `archived_until_escalating` uses to decide whether
 * to escalate were split five ways.
 *
 * Connection faults are therefore collapsed onto [CONNECTION_FINGERPRINT], recognised from the
 * SQL standard's class-08 SQLState ("connection exception") or from the framework wrapper types
 * that stand for it. `server_name` stays on every event, so a single-instance outage remains
 * distinguishable from a shared-infrastructure one by filtering inside the issue.
 *
 * Everything else Hibernate logs - constraint violations and the like - keeps a per-message
 * fingerprint with digit runs replaced, which collapses interpolated variants without merging
 * genuinely different SQL errors.
 */
@Component
class DatabaseSentryEventProcessor : EventProcessor {
    override fun process(
        event: SentryEvent,
        hint: Hint
    ): SentryEvent {
        if (isConnectionFault(event.throwable)) {
            event.fingerprints = listOf(CONNECTION_FINGERPRINT)
            return event
        }

        if (event.logger != SQL_EXCEPTION_LOGGER) {
            return event
        }

        // The logback integration always sets `formatted`; the others are defensive fallbacks.
        val message = event.message?.formatted ?: event.message?.message ?: event.throwable?.message

        if (message != null) {
            event.fingerprints =
                if (CONNECTION_FAULT_MESSAGES.any { message.contains(it) }) {
                    // These events carry no throwable, so the SQLState is unavailable and the
                    // driver's wording is all there is to match on. Scoping the match to
                    // Hibernate's SQL exception logger keeps it from reaching other events.
                    listOf(CONNECTION_FINGERPRINT)
                } else {
                    listOf(SQL_EXCEPTION_LOGGER, message.replace(INTERPOLATED_NUMBERS, "#"))
                }
        }

        return event
    }

    /**
     * Walks the cause chain for a lost or unobtainable database connection. The depth bound guards
     * against self-referential chains, which some drivers produce.
     */
    private fun isConnectionFault(throwable: Throwable?): Boolean {
        var cause = throwable
        var depth = 0

        while (cause != null && depth < MAX_CAUSE_DEPTH) {
            if (cause is JDBCConnectionException || cause is CannotGetJdbcConnectionException) {
                return true
            }

            // The SQL standard reserves SQLState class 08 for connection exceptions; the PostgreSQL
            // driver and HikariCP's pool timeout both report it.
            if (cause is SQLException && cause.sqlState?.startsWith(CONNECTION_SQL_STATE_CLASS) == true) {
                return true
            }

            cause = cause.cause
            depth += 1
        }

        return false
    }

    companion object {
        const val SQL_EXCEPTION_LOGGER = "org.hibernate.engine.jdbc.spi.SqlExceptionHelper"
        const val CONNECTION_FINGERPRINT = "database-connection-unavailable"

        private const val CONNECTION_SQL_STATE_CLASS = "08"
        private const val MAX_CAUSE_DEPTH = 10
        private val INTERPOLATED_NUMBERS = Regex("\\d+")

        /**
         * Wordings observed in production for a database that went away, as logged without a
         * throwable. Kept deliberately short: a wording that is not listed still groups by its
         * normalised message, which is a smaller failure than mistaking an unrelated error for a
         * connection fault.
         */
        private val CONNECTION_FAULT_MESSAGES =
            listOf(
                "An I/O error occurred while sending to the backend.",
                "This connection has been closed.",
                "The connection attempt failed.",
                "Connection is not available, request timed out after",
                "Connection refused",
                "Connection reset",
                "terminating connection"
            )
    }
}
