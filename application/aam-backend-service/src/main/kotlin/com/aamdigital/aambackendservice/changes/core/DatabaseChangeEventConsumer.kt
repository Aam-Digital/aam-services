package com.aamdigital.aambackendservice.changes.core

import com.rabbitmq.client.Channel
import org.springframework.amqp.core.Message
import reactor.core.publisher.Mono

interface DatabaseChangeEventConsumer {
    fun consume(rawMessage: String, message: Message, channel: Channel): Mono<Unit>
}
