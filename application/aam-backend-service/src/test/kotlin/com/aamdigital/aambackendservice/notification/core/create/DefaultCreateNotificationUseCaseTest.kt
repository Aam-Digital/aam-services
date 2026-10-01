package com.aamdigital.aambackendservice.notification.core.create

import com.aamdigital.aambackendservice.common.domain.UseCaseOutcome
import com.aamdigital.aambackendservice.notification.core.CreateUserNotificationEvent
import com.aamdigital.aambackendservice.notification.domain.NotificationChannelType
import com.aamdigital.aambackendservice.notification.domain.NotificationDetails
import com.aamdigital.aambackendservice.notification.domain.NotificationType
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.Test
import java.net.ConnectException

class DefaultCreateNotificationUseCaseTest {
    private val request =
        CreateNotificationRequest(
            CreateUserNotificationEvent(
                userIdentifier = "user-1",
                notificationChannelType = NotificationChannelType.EMAIL,
                notificationRule = "ext-1",
                details = NotificationDetails(notificationType = NotificationType.ENTITY_CHANGE, title = "Rule 1")
            )
        )

    private fun useCaseWithHandlerThrowing(exception: Exception) =
        DefaultCreateNotificationUseCase(
            listOf(
                object : CreateNotificationHandler {
                    override fun canHandle(notificationChannelType: NotificationChannelType) = true

                    override fun createMessage(
                        createUserNotificationEvent: CreateUserNotificationEvent
                    ): CreateNotificationData = throw exception
                }
            )
        )

    @Test
    fun `should rethrow a transient failure so the outbox can retry it`() {
        // Given
        val exception = TransientNotificationException("SMTP connection failed", ConnectException("refused"))
        val useCase = useCaseWithHandlerThrowing(exception)

        // When
        val thrown = catchThrowable { useCase.run(request) }

        // Then
        assertThat(thrown).isSameAs(exception)
    }

    @Test
    fun `should return any other failure with its cause, for the caller to log`() {
        // Given
        val exception = IllegalStateException("push service unreachable")
        val useCase = useCaseWithHandlerThrowing(exception)

        // When
        val outcome = useCase.run(request)

        // Then
        assertThat(outcome).isInstanceOf(UseCaseOutcome.Failure::class.java)
        assertThat((outcome as UseCaseOutcome.Failure).cause).isSameAs(exception)
    }
}
