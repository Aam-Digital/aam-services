package com.aamdigital.aambackendservice.notification.core.outbox

import com.aamdigital.aambackendservice.common.domain.UseCaseOutcome
import com.aamdigital.aambackendservice.common.error.AamErrorCode
import com.aamdigital.aambackendservice.notification.core.CreateUserNotificationEvent
import com.aamdigital.aambackendservice.notification.core.create.CreateNotificationData
import com.aamdigital.aambackendservice.notification.core.create.CreateNotificationUseCase
import com.aamdigital.aambackendservice.notification.domain.NotificationChannelType
import com.aamdigital.aambackendservice.notification.domain.NotificationDetails
import com.aamdigital.aambackendservice.notification.domain.NotificationType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
class OutboxUserNotificationPublisherTest {
    private enum class TestErrorCode : AamErrorCode { IO_ERROR }

    @Mock
    lateinit var notificationOutboxRepository: NotificationOutboxRepository

    @Mock
    lateinit var createNotificationUseCase: CreateNotificationUseCase

    private val details =
        NotificationDetails(
            notificationType = NotificationType.ENTITY_CHANGE,
            title = "Rule 1"
        )

    private fun event(channel: NotificationChannelType) =
        CreateUserNotificationEvent(
            userIdentifier = "user-1",
            notificationChannelType = channel,
            notificationRule = "ext-1",
            details = details
        )

    private fun publisher() =
        OutboxUserNotificationPublisher(
            notificationOutboxRepository = notificationOutboxRepository,
            createNotificationUseCase = createNotificationUseCase
        )

    @Test
    fun `should deliver an in-app notification straight away without using the outbox`() {
        // Given an in-app notification is a single idempotent CouchDB write and is what the user
        // reads, so it should not wait for a drain tick
        whenever(createNotificationUseCase.run(any()))
            .thenReturn(
                UseCaseOutcome.Success(
                    CreateNotificationData(success = true, messageCreated = true, messageReference = null)
                )
            )

        // When
        publisher().publish(event(NotificationChannelType.APP))

        // Then
        verify(createNotificationUseCase).run(any())
        verify(notificationOutboxRepository, never()).storeIfAbsent(any())
    }

    @Test
    fun `should put a push notification in the outbox rather than sending it inline`() {
        // Given
        val publisher = publisher()

        // When
        publisher.publish(event(NotificationChannelType.PUSH))

        // Then
        val captor = argumentCaptor<NotificationOutboxEntry>()
        verify(notificationOutboxRepository).storeIfAbsent(captor.capture())
        assertThat(captor.firstValue.notificationChannelType).isEqualTo(NotificationChannelType.PUSH)
        assertThat(captor.firstValue.id)
            .isEqualTo(NotificationOutboxEntry.idFor(details.id.toString(), NotificationChannelType.PUSH))
        assertThat(captor.firstValue.attempts).isEqualTo(0)
        verify(createNotificationUseCase, never()).run(any())
    }

    @Test
    fun `should put an email notification in the outbox rather than sending it inline`() {
        // Given
        val publisher = publisher()

        // When
        publisher.publish(event(NotificationChannelType.EMAIL))

        // Then
        val captor = argumentCaptor<NotificationOutboxEntry>()
        verify(notificationOutboxRepository).storeIfAbsent(captor.capture())
        assertThat(captor.firstValue.notificationChannelType).isEqualTo(NotificationChannelType.EMAIL)
        verify(createNotificationUseCase, never()).run(any())
    }

    @Test
    fun `should fall back to the outbox when the immediate in-app write fails`() {
        // Given nothing may be dropped: a failed in-app write has to be retried like any other
        whenever(createNotificationUseCase.run(any()))
            .thenReturn(
                UseCaseOutcome.Failure(
                    errorCode = TestErrorCode.IO_ERROR,
                    errorMessage = "couchdb unreachable"
                )
            )

        // When
        publisher().publish(event(NotificationChannelType.APP))

        // Then
        val captor = argumentCaptor<NotificationOutboxEntry>()
        verify(notificationOutboxRepository).storeIfAbsent(captor.capture())
        assertThat(captor.firstValue.notificationChannelType).isEqualTo(NotificationChannelType.APP)
    }

    @Test
    fun `should fall back to the outbox when the immediate in-app write throws`() {
        // Given
        whenever(createNotificationUseCase.run(any())).thenThrow(RuntimeException("boom"))

        // When
        publisher().publish(event(NotificationChannelType.APP))

        // Then
        verify(notificationOutboxRepository).storeIfAbsent(any())
    }
}
