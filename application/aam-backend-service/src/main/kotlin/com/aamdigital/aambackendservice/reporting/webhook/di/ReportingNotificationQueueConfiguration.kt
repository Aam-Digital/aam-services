package com.aamdigital.aambackendservice.reporting.webhook.di

import com.aamdigital.aambackendservice.common.queue.core.QueueMessageParser
import com.aamdigital.aambackendservice.reporting.webhook.core.TriggerWebhookUseCase
import com.aamdigital.aambackendservice.reporting.webhook.queue.WebhookEventConsumer
import com.aamdigital.aambackendservice.reporting.webhook.queue.WebhookEventPublisher
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.amqp.core.Binding
import org.springframework.amqp.core.BindingBuilder
import org.springframework.amqp.core.FanoutExchange
import org.springframework.amqp.core.Queue
import org.springframework.amqp.core.QueueBuilder
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ReportingNotificationQueueConfiguration {

    companion object {
        const val NOTIFICATION_QUEUE = "notification.webhook"
        const val NOTIFICATION_DEAD_LETTER_QUEUE = "notification.webhook.deadLetter"
        const val NOTIFICATION_DEAD_LETTER_EXCHANGE = "$NOTIFICATION_QUEUE.dlx"
    }

    @Bean("notification-queue")
    fun notificationQueue(): Queue = QueueBuilder
        .durable(NOTIFICATION_QUEUE)
        .deadLetterExchange(NOTIFICATION_DEAD_LETTER_EXCHANGE)
        .build()

    @Bean("notification-dead-letter-queue")
    fun notificationDeadLetterQueue(): Queue = QueueBuilder
        .durable(NOTIFICATION_DEAD_LETTER_QUEUE)
        .build()

    @Bean("notification-dead-letter-exchange")
    fun notificationDeadLetterExchange(): FanoutExchange =
        FanoutExchange(NOTIFICATION_DEAD_LETTER_EXCHANGE)

    @Bean
    fun notificationDeadLetterBinding(
        @Qualifier("notification-dead-letter-queue") notificationDeadLetterQueue: Queue,
        @Qualifier("notification-dead-letter-exchange") notificationDeadLetterExchange: FanoutExchange,
    ): Binding =
        BindingBuilder.bind(notificationDeadLetterQueue).to(notificationDeadLetterExchange)

    @Bean
    fun defaultNotificationEventPublisher(
        rabbitTemplate: RabbitTemplate,
        objectMapper: ObjectMapper,
    ): WebhookEventPublisher = WebhookEventPublisher(
        objectMapper = objectMapper, rabbitTemplate = rabbitTemplate
    )

    @Bean
    fun defaultNotificationEventConsumer(
        messageParser: QueueMessageParser,
        useCase: TriggerWebhookUseCase,
    ): WebhookEventConsumer = WebhookEventConsumer(messageParser, useCase)
}
