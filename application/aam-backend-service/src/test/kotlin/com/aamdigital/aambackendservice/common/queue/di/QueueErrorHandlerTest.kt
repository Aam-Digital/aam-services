package com.aamdigital.aambackendservice.common.queue.di

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.amqp.AmqpRejectAndDontRequeueException
import org.springframework.amqp.rabbit.support.ListenerExecutionFailedException

class QueueErrorHandlerTest {
    private val handler = QueueErrorHandler()
    private lateinit var logger: Logger
    private lateinit var appender: ListAppender<ILoggingEvent>

    @BeforeEach
    fun setUp() {
        logger = LoggerFactory.getLogger(QueueErrorHandler::class.java) as Logger
        appender = ListAppender<ILoggingEvent>().apply { start() }
        logger.addAppender(appender)
    }

    @AfterEach
    fun tearDown() {
        logger.detachAppender(appender)
    }

    @Test
    fun `logs the unwrapped root cause at ERROR for a wrapped listener failure`() {
        val rootCause = IllegalStateException("no such column: foo")
        // mirror how the container wraps a consumer's AmqpRejectAndDontRequeueException
        val wrapped =
            ListenerExecutionFailedException(
                "Listener failed",
                AmqpRejectAndDontRequeueException("[QUERY_FAILED] rejected", rootCause)
            )

        // contains an AmqpRejectAndDontRequeueException -> not re-thrown as fatal, just logged + rejected
        handler.handleError(wrapped)

        val errors = appender.list.filter { it.level == Level.ERROR }
        assertThat(errors).hasSize(1)
        assertThat(errors.first().formattedMessage).contains("no such column: foo")
    }
}
