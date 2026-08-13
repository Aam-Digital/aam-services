package com.aamdigital.aambackendservice.notification.queue

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.aamdigital.aambackendservice.notification.di.NotificationQueueConfiguration.Companion.USER_NOTIFICATION_DLQ
import com.aamdigital.aambackendservice.notification.di.NotificationQueueConfiguration.Companion.USER_NOTIFICATION_QUEUE
import com.rabbitmq.client.AMQP
import com.rabbitmq.client.Channel
import com.rabbitmq.client.Envelope
import com.rabbitmq.client.GetResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.slf4j.LoggerFactory
import org.springframework.amqp.AmqpConnectException
import org.springframework.amqp.AmqpIOException
import org.springframework.amqp.core.AmqpAdmin
import org.springframework.amqp.core.Queue
import org.springframework.amqp.rabbit.core.ChannelCallback
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.amqp.rabbit.support.RabbitExceptionTranslator
import java.io.IOException

@ExtendWith(MockitoExtension::class)
class NotificationDlqReprocessorTest {
    private val amqpAdmin: AmqpAdmin = mock()
    private val rabbitTemplate: RabbitTemplate = mock()
    private val channel: Channel = mock()
    private val dlq = Queue(USER_NOTIFICATION_DLQ)

    private lateinit var service: NotificationDlqReprocessor
    private lateinit var logAppender: ListAppender<ILoggingEvent>
    private lateinit var logger: Logger

    private fun getResponse(
        body: String,
        deliveryTag: Long
    ) = GetResponse(
        Envelope(deliveryTag, false, "", USER_NOTIFICATION_DLQ),
        AMQP.BasicProperties(),
        body.toByteArray(),
        0
    )

    /**
     * Runs the callback against [channel], translating failures the way the real
     * [RabbitTemplate.execute] does, so the service sees an `AmqpException` rather than a raw
     * `IOException`.
     */
    private fun stubExecuteAgainstChannel() {
        whenever(rabbitTemplate.execute<Int>(any())).thenAnswer { invocation ->
            try {
                invocation.getArgument<ChannelCallback<Int>>(0).doInRabbit(channel)
            } catch (ex: Exception) {
                throw RabbitExceptionTranslator.convertRabbitAccessException(ex)
            }
        }
    }

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
        stubExecuteAgainstChannel()
        whenever(channel.basicGet(USER_NOTIFICATION_DLQ, false))
            .thenReturn(getResponse("first", 1), getResponse("second", 2), null)

        // When
        service.reprocessDeadLetteredNotifications()

        // Then - the whole move runs inside one transaction: select first, then publish and
        // acknowledge each message, then commit. Without txSelect the acknowledgement would take
        // effect immediately and the atomicity this relies on would be gone.
        val transactedMove = inOrder(channel)
        transactedMove.verify(channel).txSelect()
        transactedMove.verify(channel).basicPublish(eq(""), eq(USER_NOTIFICATION_QUEUE), anyOrNull(), any())
        transactedMove.verify(channel).basicAck(eq(1L), eq(false))
        transactedMove.verify(channel).basicPublish(eq(""), eq(USER_NOTIFICATION_QUEUE), anyOrNull(), any())
        transactedMove.verify(channel).basicAck(eq(2L), eq(false))
        transactedMove.verify(channel).txCommit()

        assertThat(loggedAt(Level.INFO)).anyMatch { it.contains("Re-queued 2 message(s)") }
    }

    @Test
    fun `should keep a dead lettered message for redelivery when publishing it back fails`() {
        // Given
        stubExecuteAgainstChannel()
        whenever(channel.basicGet(USER_NOTIFICATION_DLQ, false)).thenReturn(getResponse("stuck", 9))
        whenever(channel.basicPublish(eq(""), eq(USER_NOTIFICATION_QUEUE), anyOrNull(), any()))
            .thenThrow(IOException("broker went away mid-drain"))

        // When
        service.reprocessDeadLetteredNotifications()

        // Then - neither acknowledged nor committed, so the broker still owns the message and
        // redelivers it on the next attempt instead of it being lost
        verify(channel, never()).basicAck(any(), any())
        verify(channel, never()).txCommit()

        // And - the failure is reported rather than silently swallowed
        assertThat(loggedAt(Level.ERROR)).hasSize(1)
    }

    @Test
    fun `should drain only once per service lifetime so a failing message is not re-queued every tick`() {
        // Given
        stubExecuteAgainstChannel()
        whenever(channel.basicGet(USER_NOTIFICATION_DLQ, false)).thenReturn(getResponse("only", 1), null)

        // When - the schedule fires repeatedly
        service.reprocessDeadLetteredNotifications()
        service.reprocessDeadLetteredNotifications()
        service.reprocessDeadLetteredNotifications()

        // Then
        verify(channel, times(1)).basicPublish(any(), any(), anyOrNull(), any())
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
        verify(rabbitTemplate, never()).execute<Int>(any())

        // And - the next tick tries again rather than giving up
        doNothing()
            .whenever(amqpAdmin)
            .declareQueue(dlq)
        stubExecuteAgainstChannel()
        whenever(channel.basicGet(USER_NOTIFICATION_DLQ, false)).thenReturn(getResponse("deferred", 3), null)

        service.reprocessDeadLetteredNotifications()

        verify(channel).basicPublish(eq(""), eq(USER_NOTIFICATION_QUEUE), anyOrNull(), any())
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
