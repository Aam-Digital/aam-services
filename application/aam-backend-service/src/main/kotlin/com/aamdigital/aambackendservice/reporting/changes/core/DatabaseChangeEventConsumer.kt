package com.aamdigital.aambackendservice.reporting.changes.core

import com.rabbitmq.client.Channel
import org.springframework.amqp.core.Message

interface DatabaseChangeEventConsumer {
    fun consume(rawMessage: String, message: Message, channel: Channel)
}
