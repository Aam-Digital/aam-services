package com.aamdigital.aambackendservice.notification

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.boot.DefaultApplicationArguments
import org.springframework.mock.env.MockEnvironment
import ch.qos.logback.classic.Logger as LogbackLogger

class NotificationEmailStartupDiagnosticsTest {
    private lateinit var logger: LogbackLogger
    private lateinit var logAppender: ListAppender<ILoggingEvent>
    private var previousLevel: Level? = null

    @BeforeEach
    fun setUp() {
        logger = LoggerFactory.getLogger(NotificationEmailStartupDiagnostics::class.java) as LogbackLogger
        logAppender = ListAppender<ILoggingEvent>().apply { start() }
        // the application.yaml default is WARN, and an earlier Spring test in the same JVM leaves it applied
        previousLevel = logger.level
        logger.level = Level.INFO
        logger.addAppender(logAppender)
    }

    @AfterEach
    fun tearDown() {
        logger.detachAppender(logAppender)
        logger.level = previousLevel
    }

    private fun runDiagnostics(environment: MockEnvironment): List<ILoggingEvent> {
        NotificationEmailStartupDiagnostics(environment).run(DefaultApplicationArguments())
        return logAppender.list
    }

    private fun fullyConfigured() =
        MockEnvironment()
            .withProperty("spring.mail.host", "smtp.example.org")
            .withProperty("notification.email.from", "notifications@example.org")
            .withProperty("keycloak.server-url", "https://keycloak.example.org")

    @Test
    fun `should log at info that email is on when everything is configured`() {
        // When
        val events = runDiagnostics(fullyConfigured())

        // Then
        assertThat(events).hasSize(1)
        assertThat(events.single().level).isEqualTo(Level.INFO)
        assertThat(events.single().formattedMessage).contains("Notification email is on")
    }

    @Test
    fun `should log at info that email is off when no SMTP host is configured`() {
        // Given
        val environment =
            MockEnvironment()
                .withProperty("spring.mail.host", "")
                .withProperty("notification.email.from", "notifications@example.org")

        // When
        val events = runDiagnostics(environment)

        // Then
        assertThat(events).hasSize(1)
        assertThat(events.single().level).isEqualTo(Level.INFO)
        assertThat(events.single().formattedMessage)
            .contains("Notification email is off")
            .contains("spring.mail.host")
    }

    @Test
    fun `should log at error naming the missing settings when an SMTP host is set but the sender address is not`() {
        // Given
        val environment = fullyConfigured().withProperty("notification.email.from", " ")

        // When
        val events = runDiagnostics(environment)

        // Then
        assertThat(events).hasSize(1)
        assertThat(events.single().level).isEqualTo(Level.ERROR)
        assertThat(events.single().formattedMessage)
            .contains("[notification.email.from]")
            .doesNotContain("keycloak.server-url")
    }

    @Test
    fun `should log at error naming the missing settings when an SMTP host is set but Keycloak is not`() {
        // Given
        val environment =
            MockEnvironment()
                .withProperty("spring.mail.host", "smtp.example.org")
                .withProperty("notification.email.from", "notifications@example.org")

        // When
        val events = runDiagnostics(environment)

        // Then
        assertThat(events).hasSize(1)
        assertThat(events.single().level).isEqualTo(Level.ERROR)
        assertThat(events.single().formattedMessage).contains("[keycloak.server-url]")
    }

    @Test
    fun `should never log the values of the settings`() {
        // Given
        val environment = fullyConfigured().withProperty("notification.email.from", "")

        // When
        val events = runDiagnostics(environment)

        // Then
        assertThat(events.map { it.formattedMessage })
            .noneMatch { it.contains("smtp.example.org") || it.contains("keycloak.example.org") }
    }

    @Test
    fun `should warn that the removed flag has no effect whatever its value`() {
        listOf("true", "false").forEach { flagValue ->
            // Given
            logAppender.list.clear()
            val environment = fullyConfigured().withProperty(NotificationEmailPrerequisites.REMOVED_FEATURE_FLAG, flagValue)

            // When
            val events = runDiagnostics(environment)

            // Then
            val warnings = events.filter { it.level == Level.WARN }
            assertThat(warnings).hasSize(1)
            assertThat(warnings.single().formattedMessage)
                .contains("features.notification-api.email.enabled")
                .contains("no longer read")
        }
    }

    @Test
    fun `should not warn about the removed flag when it is not set`() {
        // When
        val events = runDiagnostics(fullyConfigured())

        // Then
        assertThat(events).noneMatch { it.level == Level.WARN }
    }
}
