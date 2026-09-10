package com.aamdigital.aambackendservice.notification.di

import com.aamdigital.aambackendservice.common.queue.core.QueueMessageParser
import com.aamdigital.aambackendservice.notification.ConditionalOnNotificationApiEnabled
import com.aamdigital.aambackendservice.notification.core.config.NotificationConfigCache
import com.aamdigital.aambackendservice.notification.core.trigger.ApplyNotificationRulesUseCase
import com.aamdigital.aambackendservice.notification.queue.DefaultNotificationDocumentChangeConsumer
import com.aamdigital.aambackendservice.notification.queue.NotificationDocumentChangeConsumer
import org.springframework.amqp.core.Binding
import org.springframework.amqp.core.BindingBuilder
import org.springframework.amqp.core.FanoutExchange
import org.springframework.amqp.core.Queue
import org.springframework.amqp.core.QueueBuilder
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnNotificationApiEnabled
class NotificationQueueConfiguration {
    companion object {
        const val DOCUMENT_CHANGES_NOTIFICATION_QUEUE = "document.changes.notification"

        /**
         * Kept as the channel name passed to
         * [com.aamdigital.aambackendservice.notification.queue.UserNotificationPublisher]. Owed
         * notifications are held in the `notification-outbox` CouchDB database now, so no queue is
         * declared for it, but the interface still carries the queue-shaped signature. Both go away
         * with RabbitMQ.
         */
        const val USER_NOTIFICATION_QUEUE = "notification.user"
    }

    @Bean("notification-document-changes-queue")
    fun notificationDocumentChangesQueue(): Queue = QueueBuilder.durable(DOCUMENT_CHANGES_NOTIFICATION_QUEUE).build()

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
}
