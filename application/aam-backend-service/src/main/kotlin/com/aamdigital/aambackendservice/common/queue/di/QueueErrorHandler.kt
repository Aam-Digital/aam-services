package com.aamdigital.aambackendservice.common.queue.di

import com.aamdigital.aambackendservice.common.error.InvalidArgumentException
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.listener.ConditionalRejectingErrorHandler
import org.springframework.core.NestedExceptionUtils

/**
 * Central error handler for every `@RabbitListener` container.
 *
 * Spring's default [ConditionalRejectingErrorHandler] logs only the wrapping `ListenerExecutionFailedException`
 * at WARN, which (a) sits below the Sentry minimum event level, so permanent failures never become Sentry
 * events, and (b) buries the real cause under AMQP framework frames. Today the only consumer whose failures
 * reach Sentry is the one that calls `Sentry.captureException` by hand.
 *
 * This handler additionally logs the unwrapped *root cause* at ERROR, so every permanent listener failure is
 * reported once, grouped by its actual cause - while delegating the reject/requeue decision to the default
 * strategy, leaving message disposition unchanged.
 *
 * Failures caused by invalid input (an [InvalidArgumentException] anywhere in the cause chain, e.g. a
 * ReportConfig whose query SQS rejects) are logged at INFO instead: they are a configuration problem of the
 * individual instance, not a backend defect, so they must not raise Sentry alerts.
 */
class QueueErrorHandler : ConditionalRejectingErrorHandler() {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun handleError(t: Throwable) {
        val rootCause = NestedExceptionUtils.getMostSpecificCause(t)
        if (isInvalidInput(t)) {
            logger.info("RabbitMQ listener rejected invalid input: {}", rootCause.message, rootCause)
        } else {
            logger.error("RabbitMQ listener failed: {}", rootCause.message, rootCause)
        }
        super.handleError(t)
    }

    private fun isInvalidInput(t: Throwable): Boolean =
        generateSequence(t) { it.cause.takeIf { cause -> cause !== it } }
            .any { it is InvalidArgumentException }
}
