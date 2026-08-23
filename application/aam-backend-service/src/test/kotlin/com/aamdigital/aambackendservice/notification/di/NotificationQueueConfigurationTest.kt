package com.aamdigital.aambackendservice.notification.di

import com.aamdigital.aambackendservice.notification.di.NotificationQueueConfiguration.Companion.USER_NOTIFICATION_DLQ
import com.rabbitmq.client.Channel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.amqp.core.Queue
import org.springframework.amqp.rabbit.connection.Connection
import org.springframework.amqp.rabbit.connection.ConnectionFactory

/**
 * Guards the channel-transacted wiring of the dead letter drain.
 *
 * [com.aamdigital.aambackendservice.notification.queue.NotificationDlqReprocessor] moves each
 * message inside a channel transaction, which only works on a channel obtained with
 * `transactional = true`. Whether that happens is decided entirely by the `channelTransacted` flag
 * on the [org.springframework.amqp.rabbit.core.RabbitTemplate] it is handed — nothing in the
 * reprocessor itself can tell the difference, and its own unit tests mock the template away.
 *
 * Wired to the non-transacted `@Primary` template, every drain failed with
 * `UnsupportedOperationException: Cannot start transaction on non-transactional channel`, once per
 * process, across the whole fleet. This asserts the channel the drain actually asks for.
 */
class NotificationQueueConfigurationTest {
    private val configuration = NotificationQueueConfiguration()

    @Test
    fun `asks for a transactional channel when draining the dead letter queue`() {
        // Given - a connection factory that records which kind of channel is requested
        val channel: Channel = mock()
        val connection: Connection = mock()
        val connectionFactory: ConnectionFactory = mock()
        whenever(connectionFactory.createConnection()).thenReturn(connection)
        whenever(connection.createChannel(any())).thenReturn(channel)
        whenever(channel.isOpen).thenReturn(true)
        whenever(channel.basicGet(any(), any())).thenReturn(null)

        val reprocessor =
            configuration.notificationUserDlqReprocessor(
                amqpAdmin = mock(),
                dlq = Queue(USER_NOTIFICATION_DLQ),
                rabbitTemplate = configuration.notificationDlqRabbitTemplate(connectionFactory)
            )

        // When
        reprocessor.reprocessDeadLetteredNotifications()

        // Then - a non-transactional channel would reject txSelect and the drain would never run
        verify(connection).createChannel(true)
    }

    @Test
    fun `the dead letter template is channel-transacted`() {
        // Given / When
        val template = configuration.notificationDlqRabbitTemplate(mock())

        // Then
        assertThat(template.isChannelTransacted).isTrue()
    }
}
