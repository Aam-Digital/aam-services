package com.aamdigital.aambackendservice.notification.core.trigger

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.aamdigital.aambackendservice.common.changes.DocumentChangeEvent
import com.aamdigital.aambackendservice.common.domain.UseCaseOutcome
import com.aamdigital.aambackendservice.common.error.AamErrorCode
import com.aamdigital.aambackendservice.notification.core.config.NotificationConfigCache
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.slf4j.LoggerFactory
import ch.qos.logback.classic.Logger as LogbackLogger

@ExtendWith(MockitoExtension::class)
class NotificationDocumentChangeHandlerTest {
    private enum class TestErrorCode : AamErrorCode { PERMISSION_CHECK_FAILED }

    @Mock
    lateinit var notificationConfigCache: NotificationConfigCache

    @Mock
    lateinit var applyNotificationRulesUseCase: ApplyNotificationRulesUseCase

    private val handler by lazy {
        NotificationDocumentChangeHandler(
            notificationConfigCache = notificationConfigCache,
            applyNotificationRulesUseCase = applyNotificationRulesUseCase
        )
    }

    private fun event(documentId: String) =
        DocumentChangeEvent(
            database = "app",
            documentId = documentId,
            rev = "1-abc",
            currentVersion = emptyMap<String, Any>(),
            previousVersion = emptyMap<String, Any>(),
            deleted = false
        )

    @Test
    fun `should refresh the config cache instead of matching rules for a NotificationConfig change`() {
        // Given a NotificationConfig document is the rules, not something to match against them
        handler.handle(event("NotificationConfig:user-1"))

        // Then
        verify(notificationConfigCache).refreshConfig(eq("app"), eq("NotificationConfig:user-1"), eq(false))
        verify(applyNotificationRulesUseCase, never()).run(any())
    }

    @Test
    fun `should apply the notification rules to any other change`() {
        // Given
        whenever(applyNotificationRulesUseCase.run(any()))
            .thenReturn(UseCaseOutcome.Success(ApplyNotificationRulesData(1)))

        // When
        handler.handle(event("Child:1"))

        // Then
        verify(applyNotificationRulesUseCase).run(any())
    }

    @Test
    fun `should report a failed rule evaluation at ERROR without propagating it`() {
        // Given the cursor advances either way, so the log line is the only record that a
        // notification was owed and never produced - and WARN is below the Sentry event level
        whenever(applyNotificationRulesUseCase.run(any()))
            .thenReturn(
                UseCaseOutcome.Failure(
                    errorCode = TestErrorCode.PERMISSION_CHECK_FAILED,
                    errorMessage = "permission service unreachable"
                )
            )

        val logger =
            LoggerFactory.getLogger(NotificationDocumentChangeHandler::class.java) as LogbackLogger
        val logAppender = ListAppender<ILoggingEvent>().apply { start() }
        logger.addAppender(logAppender)

        // When one document cannot stall the feed, so this must not throw
        try {
            handler.handle(event("Child:1"))
        } finally {
            logger.detachAppender(logAppender)
        }

        // Then
        assertThat(logAppender.list)
            .anySatisfy { loggingEvent ->
                assertThat(loggingEvent.level).isEqualTo(Level.ERROR)
                assertThat(loggingEvent.formattedMessage)
                    .contains("Child:1")
                    .contains("PERMISSION_CHECK_FAILED")
            }
    }

    @Test
    fun `should not propagate a failed config refresh`() {
        // Given the cache is reloaded on startup, so this instance keeps the previous rules
        whenever(notificationConfigCache.refreshConfig(any(), any(), any()))
            .thenThrow(RuntimeException("couchdb unreachable"))

        // When / Then
        handler.handle(event("NotificationConfig:user-1"))
    }
}
