package com.aamdigital.aambackendservice.common.storage.di

import io.sentry.EventProcessor
import io.sentry.Hint
import io.sentry.SentryEvent
import org.springframework.stereotype.Component

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
 * HikariCP interpolates the elapsed wait and the pool counts into its message
 * ("... request timed out after 37386ms (total=7, active=0, idle=6, waiting=0)"), so no two
 * occurrences look alike and a single fault spreads across new Sentry issues as it recurs. That
 * splits the per-issue event counts which `archived_until_escalating` uses to decide whether to
 * escalate, so a recurring fault can stay archived while tracking real production outages.
 *
 * Replacing digit runs in the message collapses the interpolated variants into one issue while
 * keeping genuinely different SQL errors (constraint violations and the like) apart.
 */
@Component
class DatabaseSentryEventProcessor : EventProcessor {
    override fun process(
        event: SentryEvent,
        hint: Hint
    ): SentryEvent {
        if (event.logger != SQL_EXCEPTION_LOGGER) {
            return event
        }

        // The logback integration always sets `formatted`; the others are defensive fallbacks.
        val message = event.message?.formatted ?: event.message?.message ?: event.throwable?.message

        if (message != null) {
            event.fingerprints = listOf(SQL_EXCEPTION_LOGGER, message.replace(INTERPOLATED_NUMBERS, "#"))
        }

        return event
    }

    companion object {
        const val SQL_EXCEPTION_LOGGER = "org.hibernate.engine.jdbc.spi.SqlExceptionHelper"
        private val INTERPOLATED_NUMBERS = Regex("\\d+")
    }
}
