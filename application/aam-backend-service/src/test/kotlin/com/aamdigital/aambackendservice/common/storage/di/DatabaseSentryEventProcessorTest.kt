package com.aamdigital.aambackendservice.common.storage.di

import com.aamdigital.aambackendservice.common.storage.di.DatabaseSentryEventProcessor.Companion.CONNECTION_FINGERPRINT
import com.aamdigital.aambackendservice.common.storage.di.DatabaseSentryEventProcessor.Companion.SQL_EXCEPTION_LOGGER
import io.sentry.Hint
import io.sentry.SentryEvent
import io.sentry.exception.ExceptionMechanismException
import io.sentry.protocol.Mechanism
import io.sentry.protocol.Message
import org.assertj.core.api.Assertions.assertThat
import org.hibernate.exception.JDBCConnectionException
import org.junit.jupiter.api.Test
import java.sql.SQLException
import java.sql.SQLTransientConnectionException

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

    private fun exceptionEvent(throwable: Throwable): SentryEvent = SentryEvent(throwable)

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
    fun `leaves unrelated exceptions untouched`() {
        // Given - a connection failure against a different system must not look like a database one
        val event = exceptionEvent(IllegalStateException("502 Bad Gateway on GET request for certs"))

        // When
        val result = processor.process(event, Hint())

        // Then
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
        assertThat(shortWaitResult.fingerprints).containsExactly(CONNECTION_FINGERPRINT)
        assertThat(longWaitResult.fingerprints).isEqualTo(shortWaitResult.fingerprints)
    }

    @Test
    fun `groups the driver wordings of a lost connection into one issue`() {
        // Given - the messages Hibernate logged for one production outage, each its own issue before
        val messages =
            listOf(
                "An I/O error occurred while sending to the backend.",
                "This connection has been closed.",
                "The connection attempt failed.",
                "HikariPool-1 - Connection is not available, request timed out after 269290ms " +
                    "(total=8, active=0, idle=8, waiting=0)"
            )

        // When
        val fingerprints = messages.map { processor.process(sqlEvent(it), Hint()).fingerprints }

        // Then
        assertThat(fingerprints).containsOnly(listOf(CONNECTION_FINGERPRINT))
    }

    @Test
    fun `groups the translated connection exception with the logged driver wordings`() {
        // Given - the same outage as reported by the transaction interceptor, statement included
        val event =
            exceptionEvent(
                JDBCConnectionException(
                    "JDBC exception executing SQL [select se1_0.id from couchdb_sync_entry se1_0 where se1_0.id=?]",
                    SQLException(
                        "An I/O error occurred while sending to the backend.",
                        // SQLState 08006, "connection failure", as the PostgreSQL driver reports it
                        "08006"
                    )
                )
            )

        // When
        val result = processor.process(event, Hint())

        // Then
        assertThat(result.fingerprints).containsExactly(CONNECTION_FINGERPRINT)
    }

    @Test
    fun `groups a connection fault reported through the sentry exception mechanism`() {
        // Given - the logback integration wraps the throwable before handing it to the processors
        val connectionLost =
            SQLTransientConnectionException(
                "Connection is not available",
                // any class-08 SQLState stands for "connection exception"
                "08003"
            )
        val event = exceptionEvent(ExceptionMechanismException(Mechanism(), connectionLost, Thread.currentThread()))

        // When
        val result = processor.process(event, Hint())

        // Then
        assertThat(result.fingerprints).containsExactly(CONNECTION_FINGERPRINT)
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
        assertThat(constraintViolationResult.fingerprints).containsExactly(
            SQL_EXCEPTION_LOGGER,
            "ERROR: duplicate key value violates unique constraint"
        )
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
