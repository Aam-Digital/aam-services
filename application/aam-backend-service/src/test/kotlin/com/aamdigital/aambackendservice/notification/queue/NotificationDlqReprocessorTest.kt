package com.aamdigital.aambackendservice.notification.queue

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.aamdigital.aambackendservice.notification.di.NotificationQueueConfiguration.Companion.USER_NOTIFICATION_DLQ
import com.aamdigital.aambackendservice.notification.di.NotificationQueueConfiguration.Companion.USER_NOTIFICATION_QUEUE
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.slf4j.LoggerFactory
import org.springframework.amqp.AmqpConnectException
import org.springframework.amqp.AmqpIOException
import org.springframework.amqp.core.AmqpAdmin
import org.springframework.amqp.core.Message
import org.springframework.amqp.core.MessageProperties
import org.springframework.amqp.core.Queue
import org.springframework.amqp.rabbit.core.RabbitTemplate
import java.io.IOException

@ExtendWith(MockitoExtension::class)
class NotificationDlqReprocessorTest {
    private val amqpAdmin: AmqpAdmin = mock()
    private val rabbitTemplate: RabbitTemplate = mock()
    private val dlq = Queue(USER_NOTIFICATION_DLQ)

    private lateinit var service: NotificationDlqReprocessor
    private lateinit var logAppender: ListAppender<ILoggingEvent>
    private lateinit var logger: Logger

    private fun message(body: String) = Message(body.toByteArray(), MessageProperties())

    private fun loggedAt(level: Level): List<String> =
        logAppender.list.filter { it.level == level }.map { it.formattedMessage }

    @BeforeEach
    fun setUp() {
        service = NotificationDlqReprocessor(amqpAdmin, dlq, rabbitTemplate)

        logger = LoggerFactory.getLogger(NotificationDlqReprocessor::class.java) as Logger
        logAppender = ListAppender<ILoggingEvent>().apply { start() }
        logger.addAppender(logAppender)
    }

    @AfterEach
    fun tearDown() {
        logger.detachAppender(logAppender)
    }

    @Test
    fun `should re-queue all dead lettered messages back onto the notification queue`() {
        // Given
        val first = message("first")
        val second = message("second")
        whenever(rabbitTemplate.receive(USER_NOTIFICATION_DLQ)).thenReturn(first, second, null)

        // When
        service.reprocessDeadLetteredNotifications()

        // Then
        verify(rabbitTemplate).send(eq(""), eq(USER_NOTIFICATION_QUEUE), eq(first))
        verify(rabbitTemplate).send(eq(""), eq(USER_NOTIFICATION_QUEUE), eq(second))
        assertThat(loggedAt(Level.INFO)).anyMatch { it.contains("Re-queued 2 message(s)") }
    }

    @Test
    fun `should drain only once per service lifetime so a failing message is not re-queued every tick`() {
        // Given
        whenever(rabbitTemplate.receive(USER_NOTIFICATION_DLQ)).thenReturn(message("only"), null)

        // When - the schedule fires repeatedly
        service.reprocessDeadLetteredNotifications()
        service.reprocessDeadLetteredNotifications()
        service.reprocessDeadLetteredNotifications()

        // Then
        verify(rabbitTemplate, times(1)).send(any(), any<String>(), any<Message>())
    }

    @Test
    fun `should stay below the Sentry threshold and retry when the broker is unreachable`() {
        // Given
        doThrow(AmqpConnectException(RuntimeException("Connection refused")))
            .whenever(amqpAdmin)
            .declareQueue(dlq)

        // When
        service.reprocessDeadLetteredNotifications()

        // Then - WARN sits below Sentry's minimum event level, so broker restarts stay out of Sentry
        assertThat(loggedAt(Level.WARN)).anyMatch { it.contains("Broker unreachable") }
        assertThat(loggedAt(Level.ERROR)).isEmpty()
        verify(rabbitTemplate, never()).receive(any<String>())

        // And - the next tick tries again rather than giving up
        doNothing()
            .whenever(amqpAdmin)
            .declareQueue(dlq)
        whenever(rabbitTemplate.receive(USER_NOTIFICATION_DLQ)).thenReturn(message("deferred"), null)

        service.reprocessDeadLetteredNotifications()

        verify(rabbitTemplate).send(eq(""), eq(USER_NOTIFICATION_QUEUE), any<Message>())
    }

    @Test
    fun `should report a permanent failure once and then stop repeating it`() {
        // Given - e.g. a 406 from a queue previously declared with different arguments
        doThrow(AmqpIOException(IOException("PRECONDITION_FAILED")))
            .whenever(amqpAdmin)
            .declareQueue(dlq)

        // When - the schedule fires repeatedly
        service.reprocessDeadLetteredNotifications()
        service.reprocessDeadLetteredNotifications()
        service.reprocessDeadLetteredNotifications()

        // Then - visible in Sentry, but exactly once rather than on every tick
        assertThat(loggedAt(Level.ERROR)).hasSize(1)
        assertThat(loggedAt(Level.ERROR).first()).contains("Could not reprocess dead lettered notifications")
    }
}
