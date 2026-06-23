package com.aamdigital.aambackendservice.common.queue.di

import com.aamdigital.aambackendservice.common.queue.core.DefaultQueueMessageParser
import com.aamdigital.aambackendservice.common.queue.core.QueueMessageParser
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.amqp.AmqpRejectAndDontRequeueException
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter
import org.springframework.amqp.support.converter.MessageConverter
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.amqp.RabbitRetryTemplateCustomizer
import org.springframework.boot.autoconfigure.amqp.RabbitTemplateConfigurer
import org.springframework.boot.autoconfigure.amqp.RabbitTemplateCustomizer
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.retry.policy.SimpleRetryPolicy
import org.springframework.util.ErrorHandler

@Configuration
class QueueConfiguration {
    @Bean
    @Primary
    fun rabbitTemplate(
        configurer: RabbitTemplateConfigurer,
        connectionFactory: ConnectionFactory,
        customizers: ObjectProvider<RabbitTemplateCustomizer>
    ): RabbitTemplate {
        val template = RabbitTemplate()
        configurer.configure(template, connectionFactory)
        customizers.orderedStream().forEach { customizer: RabbitTemplateCustomizer ->
            customizer.customize(template)
        }
        template.setObservationEnabled(true)
        return template
    }

    /**
     * Restricts the listener retry interceptor (`spring.rabbitmq.listener.simple.retry`) to *transient*
     * failures only.
     *
     * [AmqpRejectAndDontRequeueException] signals a permanent failure: retrying it can never succeed, and
     * with a single-threaded consumer (`prefetch: 1`) the retry backoff blocks the whole queue for the full
     * retry window — starving unrelated messages behind it. We therefore classify it as non-retryable so it
     * is handed straight to the recoverer (dead-letter for the user-notification queue, reject-and-drop
     * elsewhere), while transient errors (e.g. `TransientNotificationException` for SMTP hiccups) are still
     * retried with backoff.
     */
    @Bean
    fun nonRetryablePermanentFailures(
        @Value("\${spring.rabbitmq.listener.simple.retry.max-attempts:3}") maxAttempts: Int
    ): RabbitRetryTemplateCustomizer =
        RabbitRetryTemplateCustomizer { target, retryTemplate ->
            if (target == RabbitRetryTemplateCustomizer.Target.LISTENER) {
                retryTemplate.setRetryPolicy(
                    SimpleRetryPolicy(
                        maxAttempts,
                        mapOf<Class<out Throwable>, Boolean>(AmqpRejectAndDontRequeueException::class.java to false),
                        true,
                        true,
                    )
                )
            }
        }

    @Bean
    fun defaultQueueMessageParser(objectMapper: ObjectMapper): QueueMessageParser =
        DefaultQueueMessageParser(
            objectMapper = objectMapper
        )

    @Bean
    fun defaultMessageConverter(): MessageConverter = Jackson2JsonMessageConverter()

    /**
     * Central [ErrorHandler] for all listener containers. See [QueueErrorHandler] - it logs the unwrapped root
     * cause of every permanent listener failure at ERROR so it is reported once to Sentry, grouped by its real
     * cause, instead of each consumer reporting (or silently dropping) errors on its own.
     */
    @Bean
    fun queueErrorHandler(): ErrorHandler = QueueErrorHandler()

    /**
     * Override Boot's auto-configured listener container factory (same name, so Boot backs off) purely to
     * attach [queueErrorHandler]. The [SimpleRabbitListenerContainerFactoryConfigurer] applies all the usual
     * `spring.rabbitmq.listener.simple.*` settings, the retry advice (incl. [nonRetryablePermanentFailures])
     * and the message converter, so nothing else changes.
     */
    @Bean
    fun rabbitListenerContainerFactory(
        configurer: SimpleRabbitListenerContainerFactoryConfigurer,
        connectionFactory: ConnectionFactory,
        queueErrorHandler: ErrorHandler,
    ): SimpleRabbitListenerContainerFactory {
        val factory = SimpleRabbitListenerContainerFactory()
        configurer.configure(factory, connectionFactory)
        factory.setErrorHandler(queueErrorHandler)
        return factory
    }
}
