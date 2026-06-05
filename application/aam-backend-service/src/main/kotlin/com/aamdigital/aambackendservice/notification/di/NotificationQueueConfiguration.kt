package com.aamdigital.aambackendservice.notification.di

import com.aamdigital.aambackendservice.common.queue.core.QueueMessageParser
import com.aamdigital.aambackendservice.notification.ConditionalOnNotificationApiEnabled
import com.aamdigital.aambackendservice.notification.core.config.NotificationConfigCache
import com.aamdigital.aambackendservice.notification.core.create.CreateNotificationUseCase
import com.aamdigital.aambackendservice.notification.core.trigger.ApplyNotificationRulesUseCase
import com.aamdigital.aambackendservice.notification.queue.DefaultNotificationDocumentChangeConsumer
import com.aamdigital.aambackendservice.notification.queue.DefaultUserNotificationConsumer
import com.aamdigital.aambackendservice.notification.queue.DefaultUserNotificationPublisher
import com.aamdigital.aambackendservice.notification.queue.NotificationDocumentChangeConsumer
import com.aamdigital.aambackendservice.notification.queue.StartupNotificationDlqReprocessor
import com.aamdigital.aambackendservice.notification.queue.UserNotificationConsumer
import com.aamdigital.aambackendservice.notification.queue.UserNotificationPublisher
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.amqp.core.Binding
import org.springframework.amqp.core.BindingBuilder
import org.springframework.amqp.core.FanoutExchange
import org.springframework.amqp.core.Queue
import org.springframework.amqp.core.QueueBuilder
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.amqp.rabbit.retry.MessageRecoverer
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer
import org.springframework.amqp.rabbit.retry.RepublishMessageRecoverer
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnNotificationApiEnabled
class NotificationQueueConfiguration {
    companion object {
        const val DOCUMENT_CHANGES_NOTIFICATION_QUEUE = "document.changes.notification"
        const val USER_NOTIFICATION_QUEUE = "notification.user"
        const val USER_NOTIFICATION_DLQ = "notification.user.dlq"
    }

    @Bean("notification-document-changes-queue")
    fun notificationDocumentChangesQueue(): Queue = QueueBuilder.durable(DOCUMENT_CHANGES_NOTIFICATION_QUEUE).build()

    @Bean("notification-user-notification-queue")
    fun notificationUserNotificationQueue(): Queue = QueueBuilder.durable(USER_NOTIFICATION_QUEUE).build()

    @Bean("notification-user-notification-dlq")
    fun notificationUserNotificationDlq(): Queue = QueueBuilder.durable(USER_NOTIFICATION_DLQ).build()

    /**
     * Routes exhausted/permanently-failed messages to a dead-letter queue once the retry interceptor
     * (see `spring.rabbitmq.listener.simple.retry`) gives up.
     *
     * We deliberately do *not* configure `x-dead-letter-exchange` on [USER_NOTIFICATION_QUEUE]: that
     * queue already exists in deployed environments without the argument, and RabbitMQ rejects any
     * redeclaration with changed arguments (406 PRECONDITION_FAILED). Recovering in the application
     * layer instead keeps the queue definition unchanged, so existing deployments start cleanly.
     *
     * This is the single, shared [MessageRecoverer] for all listeners, so it must preserve the default
     * reject-and-drop behaviour for every queue other than [USER_NOTIFICATION_QUEUE], which is the only
     * one with a dead-letter queue to recover into.
     */
    @Bean
    fun notificationMessageRecoverer(rabbitTemplate: RabbitTemplate): MessageRecoverer {
        val toDeadLetterQueue = RepublishMessageRecoverer(rabbitTemplate, "", USER_NOTIFICATION_DLQ)
        val rejectAndDrop = RejectAndDontRequeueRecoverer()
        return MessageRecoverer { message, cause ->
            if (message.messageProperties.consumerQueue == USER_NOTIFICATION_QUEUE) {
                toDeadLetterQueue.recover(message, cause)
            } else {
                rejectAndDrop.recover(message, cause)
            }
        }
    }

    @Bean("notification-document-changes-exchange")
    fun notificationDocumentChangesBinding(
        @Qualifier("notification-document-changes-queue") queue: Queue,
        @Qualifier("document-changes-exchange") exchange: FanoutExchange
    ): Binding = BindingBuilder.bind(queue).to(exchange)

    @Bean("notification-document-changes-consumer")
    fun notificationDocumentChangeEventConsumer(
        messageParser: QueueMessageParser,
        notificationConfigCache: NotificationConfigCache,
        applyNotificationRulesUseCase: ApplyNotificationRulesUseCase
    ): NotificationDocumentChangeConsumer =
        DefaultNotificationDocumentChangeConsumer(
            messageParser,
            notificationConfigCache,
            applyNotificationRulesUseCase
        )

    @Bean("notification-user-notification-consumer")
    fun notificationUserNotificationEventConsumer(
        messageParser: QueueMessageParser,
        createUserUseCase: CreateNotificationUseCase
    ): UserNotificationConsumer =
        DefaultUserNotificationConsumer(
            messageParser,
            createUserUseCase
        )

    @Bean("notification-user-device-publisher")
    fun notificationUserDevicePublisher(
        objectMapper: ObjectMapper,
        rabbitTemplate: RabbitTemplate
    ): UserNotificationPublisher =
        DefaultUserNotificationPublisher(
            objectMapper = objectMapper,
            rabbitTemplate = rabbitTemplate
        )

    @Bean("notification-user-dlq-reprocessor")
    fun notificationUserDlqReprocessor(
        connectionFactory: ConnectionFactory,
        @Qualifier("notification-user-notification-dlq") dlq: Queue,
        rabbitTemplate: RabbitTemplate,
    ): StartupNotificationDlqReprocessor =
        StartupNotificationDlqReprocessor(connectionFactory, dlq, rabbitTemplate)
}
