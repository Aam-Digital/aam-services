package com.aamdigital.aambackendservice.common.queue.di

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.aamdigital.aambackendservice.common.error.ExternalSystemException
import com.aamdigital.aambackendservice.common.error.InvalidArgumentException
import com.aamdigital.aambackendservice.reporting.report.sqs.SqsQueryStorage
import com.aamdigital.aambackendservice.reporting.webhook.core.WebhookCallbackRejectedException
import com.aamdigital.aambackendservice.reporting.webhook.queue.WebhookEventConsumer
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

    @Test
    fun `logs the webhook receiver's clear rejection message rather than the raw HTTP exception`() {
        // mirrors the real chain: container wrapper -> AmqpRejectAndDontRequeueException ->
        // ExternalSystemException (built in WebhookEventConsumer) -> our leaf exception (no cause),
        // which is what makes it the "most specific cause" QueueErrorHandler logs.
        val rejection =
            WebhookCallbackRejectedException(
                "Webhook receiver example.org rejected our callback for webhook webhook-1: " +
                    "403 Forbidden: \"{\"detail\":\"Invalid token.\"}\""
            )
        val aamEx =
            ExternalSystemException(
                "[USECASE_ERROR] ${rejection.localizedMessage}",
                rejection,
                code = WebhookEventConsumer.WebhookError.WEBHOOK_EVENT_TRIGGER_ERROR
            )
        val wrapped =
            ListenerExecutionFailedException(
                "Listener failed",
                AmqpRejectAndDontRequeueException(aamEx)
            )

        handler.handleError(wrapped)

        val errors = appender.list.filter { it.level == Level.ERROR }
        assertThat(errors).hasSize(1)
        assertThat(errors.first().formattedMessage)
            .contains("Webhook receiver example.org rejected our callback")
            .doesNotContain("HttpClientErrorException")
    }

    @Test
    fun `logs an invalid-input failure at INFO so it does not reach Sentry`() {
        // mirrors a ReportConfig query that SQS rejects with 400: the use case re-wraps the
        // InvalidArgumentException from SqsQueryStorage, the consumer rejects the message
        val sqsRejection =
            InvalidArgumentException(
                "[SqsQueryStorage] SQS rejected the query for report 'ReportConfig:1' (400 BAD_REQUEST): " +
                    "near \"FROM\": syntax error",
                code = SqsQueryStorage.SqsQueryStorageErrorCode.QUERY_FAILED
            )
        val useCaseEx =
            InvalidArgumentException(
                sqsRejection.localizedMessage,
                sqsRejection,
                code = sqsRejection.code
            )
        val wrapped =
            ListenerExecutionFailedException(
                "Listener failed",
                AmqpRejectAndDontRequeueException(useCaseEx)
            )

        handler.handleError(wrapped)

        assertThat(appender.list.filter { it.level == Level.ERROR }).isEmpty()
        val infos = appender.list.filter { it.level == Level.INFO }
        assertThat(infos).hasSize(1)
        assertThat(infos.first().formattedMessage).contains("near \"FROM\": syntax error")
    }
}
