package com.aamdigital.aambackendservice.notification.core.trigger

import com.aamdigital.aambackendservice.common.changes.DocumentChangeEvent
import com.aamdigital.aambackendservice.common.domain.UseCaseOutcome
import com.aamdigital.aambackendservice.common.error.AamErrorCode
import com.aamdigital.aambackendservice.notification.core.config.NotificationConfigCache
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

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
    fun `should not propagate a failed rule evaluation so one document cannot stall the feed`() {
        // Given
        whenever(applyNotificationRulesUseCase.run(any()))
            .thenReturn(
                UseCaseOutcome.Failure(
                    errorCode = TestErrorCode.PERMISSION_CHECK_FAILED,
                    errorMessage = "permission service unreachable"
                )
            )

        // When / Then
        handler.handle(event("Child:1"))
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
