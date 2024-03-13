package com.aamdigital.aambackendservice.reporting.notification.core

import com.rabbitmq.client.Channel
import org.springframework.amqp.core.Message
import reactor.core.publisher.Mono

interface NotificationEventConsumer {
    fun consume(rawMessage: String, message: Message, channel: Channel): Mono<Unit>
}
