package com.aamdigital.aambackendservice.notification.di

import com.aamdigital.aambackendservice.notification.core.trigger.ApplyNotificationRulesUseCase
import com.aamdigital.aambackendservice.notification.core.create.CreateNotificationUseCase
import com.aamdigital.aambackendservice.notification.queue.DefaultNotificationDocumentChangeConsumer
import com.aamdigital.aambackendservice.notification.queue.DefaultUserNotificationConsumer
import com.aamdigital.aambackendservice.notification.queue.DefaultUserNotificationPublisher
import com.aamdigital.aambackendservice.notification.queue.NotificationDocumentChangeConsumer
import com.aamdigital.aambackendservice.notification.core.config.SyncNotificationConfigUseCase
import com.aamdigital.aambackendservice.notification.queue.UserNotificationConsumer
import com.aamdigital.aambackendservice.notification.queue.UserNotificationPublisher
import com.aamdigital.aambackendservice.queue.core.QueueMessageParser
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.amqp.core.Binding
import org.springframework.amqp.core.BindingBuilder
import org.springframework.amqp.core.FanoutExchange
import org.springframework.amqp.core.Queue
import org.springframework.amqp.core.QueueBuilder
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnProperty(
    prefix = "features.notification-api",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = false
)
class NotificationQueueConfiguration {

    companion object {
        const val DOCUMENT_CHANGES_NOTIFICATION_QUEUE = "document.changes.notification"
        const val USER_NOTIFICATION_QUEUE = "notification.user"
    }

    @Bean("notification-document-changes-queue")
    fun notificationDocumentChangesQueue(): Queue = QueueBuilder.durable(DOCUMENT_CHANGES_NOTIFICATION_QUEUE).build()

    @Bean("notification-user-notification-queue")
    fun notificationUserNotificationQueue(): Queue = QueueBuilder.durable(USER_NOTIFICATION_QUEUE).build()

    @Bean("notification-document-changes-exchange")
    fun notificationDocumentChangesBinding(
        @Qualifier("notification-document-changes-queue") queue: Queue,
        @Qualifier("document-changes-exchange") exchange: FanoutExchange,
    ): Binding = BindingBuilder.bind(queue).to(exchange)

    @Bean("notification-document-changes-consumer")
    fun notificationDocumentChangeEventConsumer(
        messageParser: QueueMessageParser,
        syncNotificationConfigUseCase: SyncNotificationConfigUseCase,
        applyNotificationRulesUseCase: ApplyNotificationRulesUseCase,
    ): NotificationDocumentChangeConsumer = DefaultNotificationDocumentChangeConsumer(
        messageParser,
        syncNotificationConfigUseCase,
        applyNotificationRulesUseCase,
    )

    @Bean("notification-user-notification-consumer")
    fun notificationUserNotificationEventConsumer(
        messageParser: QueueMessageParser,
        createUserUseCase: CreateNotificationUseCase,
    ): UserNotificationConsumer = DefaultUserNotificationConsumer(
        messageParser,
        createUserUseCase,
    )

    @Bean("notification-user-device-publisher")
    fun notificationUserDevicePublisher(
        objectMapper: ObjectMapper,
        rabbitTemplate: RabbitTemplate,
    ): UserNotificationPublisher = DefaultUserNotificationPublisher(
        objectMapper = objectMapper,
        rabbitTemplate = rabbitTemplate,
    )
}
