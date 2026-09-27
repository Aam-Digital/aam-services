package com.aamdigital.aambackendservice.notification.core.outbox

import com.aamdigital.aambackendservice.common.outbox.Outbox
import com.aamdigital.aambackendservice.common.outbox.OutboxHandler
import com.aamdigital.aambackendservice.notification.core.CreateUserNotificationEvent
import com.aamdigital.aambackendservice.notification.domain.NotificationChannelType
import com.aamdigital.aambackendservice.notification.domain.NotificationDetails
import com.aamdigital.aambackendservice.notification.domain.NotificationType
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

class OutboxUserNotificationPublisherTest {
    private val notificationOutbox = mock<Outbox<CreateUserNotificationEvent>>()
    private val notificationOutboxHandler = mock<OutboxHandler<CreateUserNotificationEvent>>()
    private val publisher = OutboxUserNotificationPublisher(notificationOutbox, notificationOutboxHandler)

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

    @Test
    fun `should try to deliver an in-app notification straight away`() {
        // Given an in-app notification is a single idempotent CouchDB write and is what the user
        // reads, so it should not wait for a drain tick
        val event = event(NotificationChannelType.APP)

        // When
        publisher.publish(event)

        // Then
        verify(notificationOutbox)
            .deliverNowOrEnqueue(eq("${details.id}:APP"), eq(event), eq(notificationOutboxHandler))
        verify(notificationOutbox, never()).enqueue(any(), any())
    }

    @Test
    fun `should put a push notification in the outbox rather than sending it inline`() {
        // Given
        val event = event(NotificationChannelType.PUSH)

        // When
        publisher.publish(event)

        // Then the key is derived from the notification, so a replayed change is not sent twice
        verify(notificationOutbox).enqueue(eq("${details.id}:PUSH"), eq(event))
        verify(notificationOutbox, never()).deliverNowOrEnqueue(any(), any(), any())
    }

    @Test
    fun `should put an email notification in the outbox rather than sending it inline`() {
        // Given
        val event = event(NotificationChannelType.EMAIL)

        // When
        publisher.publish(event)

        // Then
        verify(notificationOutbox).enqueue(eq("${details.id}:EMAIL"), eq(event))
    }
}
