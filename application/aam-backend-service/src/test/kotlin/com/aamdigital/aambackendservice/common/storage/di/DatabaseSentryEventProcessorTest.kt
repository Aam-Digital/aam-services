package com.aamdigital.aambackendservice.common.storage.di

import com.aamdigital.aambackendservice.common.storage.di.DatabaseSentryEventProcessor.Companion.SQL_EXCEPTION_LOGGER
import io.sentry.Hint
import io.sentry.SentryEvent
import io.sentry.protocol.Message
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DatabaseSentryEventProcessorTest {
    private val processor = DatabaseSentryEventProcessor()

    private fun sqlEvent(
        message: String,
        logger: String = SQL_EXCEPTION_LOGGER
    ): SentryEvent =
        SentryEvent().apply {
            this.logger = logger
            this.message = Message().apply { formatted = message }
        }

    @Test
    fun `leaves events from other loggers untouched`() {
        // Given
        val event = sqlEvent(message = "some unrelated error", logger = "com.example.OtherLogger")

        // When
        val result = processor.process(event, Hint())

        // Then
        assertThat(result).isSameAs(event)
        assertThat(result.fingerprints).isNull()
    }

    @Test
    fun `groups pool timeouts that differ only in the interpolated wait and pool counts`() {
        // Given - the two messages that HikariCP produced for the same fault in production
        val shortWaitMessage =
            "HikariPool-1 - Connection is not available, request timed out after 37386ms " +
                "(total=7, active=0, idle=6, waiting=0)"
        val longWaitMessage =
            "HikariPool-1 - Connection is not available, request timed out after 692895ms " +
                "(total=9, active=0, idle=9, waiting=0)"

        val shortWait = sqlEvent(shortWaitMessage)
        val longWait = sqlEvent(longWaitMessage)

        // When
        val shortWaitResult = processor.process(shortWait, Hint())
        val longWaitResult = processor.process(longWait, Hint())

        // Then
        assertThat(shortWaitResult.fingerprints).isEqualTo(longWaitResult.fingerprints)
        assertThat(shortWaitResult.fingerprints).containsExactly(
            SQL_EXCEPTION_LOGGER,
            "HikariPool-# - Connection is not available, request timed out after #ms " +
                "(total=#, active=#, idle=#, waiting=#)"
        )
    }

    @Test
    fun `keeps genuinely different sql errors in separate groups`() {
        // Given
        val connectionClosed = sqlEvent("This connection has been closed.")
        val constraintViolation = sqlEvent("ERROR: duplicate key value violates unique constraint")

        // When
        val connectionClosedResult = processor.process(connectionClosed, Hint())
        val constraintViolationResult = processor.process(constraintViolation, Hint())

        // Then
        assertThat(connectionClosedResult.fingerprints)
            .isNotEqualTo(constraintViolationResult.fingerprints)
    }

    @Test
    fun `forwards the event unchanged when it carries no message`() {
        // Given
        val event = SentryEvent().apply { logger = SQL_EXCEPTION_LOGGER }

        // When
        val result = processor.process(event, Hint())

        // Then
        assertThat(result).isSameAs(event)
        assertThat(result.fingerprints).isNull()
    }
}
