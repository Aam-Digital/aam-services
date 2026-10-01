package com.aamdigital.aambackendservice.notification.core.outbox

import com.aamdigital.aambackendservice.common.domain.DomainUseCase
import com.aamdigital.aambackendservice.common.domain.UseCaseOutcome
import com.aamdigital.aambackendservice.common.error.AamErrorCode
import com.aamdigital.aambackendservice.common.outbox.OutboxDeliveryResult
import com.aamdigital.aambackendservice.notification.core.CreateUserNotificationEvent
import com.aamdigital.aambackendservice.notification.core.create.CreateNotificationData
import com.aamdigital.aambackendservice.notification.core.create.CreateNotificationUseCase
import com.aamdigital.aambackendservice.notification.core.create.TransientNotificationException
import com.aamdigital.aambackendservice.notification.domain.NotificationChannelType
import com.aamdigital.aambackendservice.notification.domain.NotificationDetails
import com.aamdigital.aambackendservice.notification.domain.NotificationType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.net.ConnectException

class NotificationOutboxHandlerTest {
    private enum class TestErrorCode : AamErrorCode { INVALID_NOTIFICATION_CHANNEL_TYPE }

    private val createNotificationUseCase = mock<CreateNotificationUseCase>()
    private val handler = NotificationOutboxHandler(createNotificationUseCase)

    private val event =
        CreateUserNotificationEvent(
            userIdentifier = "user-1",
            notificationChannelType = NotificationChannelType.EMAIL,
            notificationRule = "ext-1",
            details = NotificationDetails(notificationType = NotificationType.ENTITY_CHANGE, title = "Rule 1")
        )

    @Test
    fun `should report a created notification as delivered`() {
        // Given
        whenever(createNotificationUseCase.run(any()))
            .thenReturn(
                UseCaseOutcome.Success(
                    CreateNotificationData(success = true, messageCreated = true, messageReference = null)
                )
            )

        // Then
        assertThat(handler.deliver(event)).isEqualTo(OutboxDeliveryResult.Delivered)
    }

    @Test
    fun `should retry a transient failure`() {
        // Given
        whenever(createNotificationUseCase.run(any()))
            .thenThrow(TransientNotificationException("SMTP connection failed", ConnectException("refused")))

        // When
        val result = handler.deliver(event)

        // Then
        assertThat(result).isInstanceOf(OutboxDeliveryResult.RetryLater::class.java)
        assertThat((result as OutboxDeliveryResult.RetryLater).reason).contains("SMTP connection failed")
    }

    @Test
    fun `should reject a failure that retrying cannot fix`() {
        // Given
        whenever(createNotificationUseCase.run(any()))
            .thenReturn(
                UseCaseOutcome.Failure(
                    errorCode = TestErrorCode.INVALID_NOTIFICATION_CHANNEL_TYPE,
                    errorMessage = "No Handler for this NotificationChannelType"
                )
            )

        // When
        val result = handler.deliver(event)

        // Then
        assertThat(result).isInstanceOf(OutboxDeliveryResult.Rejected::class.java)
        assertThat((result as OutboxDeliveryResult.Rejected).reason)
            .isEqualTo("[INVALID_NOTIFICATION_CHANNEL_TYPE] No Handler for this NotificationChannelType")
    }

    @Test
    fun `should hand the failure's cause to the outbox, which logs it`() {
        // Given
        val cause = IllegalStateException("push service unreachable")
        whenever(createNotificationUseCase.run(any()))
            .thenReturn(
                UseCaseOutcome.Failure(
                    errorCode = DomainUseCase.DomainError.UNHANDLED_EXCEPTION_IN_USE_CASE,
                    errorMessage = "push service unreachable",
                    cause = cause
                )
            )

        // When
        val result = handler.deliver(event)

        // Then
        assertThat(result).isInstanceOf(OutboxDeliveryResult.Rejected::class.java)
        assertThat((result as OutboxDeliveryResult.Rejected).cause).isSameAs(cause)
    }
}
