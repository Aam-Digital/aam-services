package com.aamdigital.aambackendservice.common.queue.di

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
 */
class QueueErrorHandler : ConditionalRejectingErrorHandler() {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun handleError(t: Throwable) {
        val rootCause = NestedExceptionUtils.getMostSpecificCause(t)
        logger.error("RabbitMQ listener failed: {}", rootCause.message, rootCause)
        super.handleError(t)
    }
}
