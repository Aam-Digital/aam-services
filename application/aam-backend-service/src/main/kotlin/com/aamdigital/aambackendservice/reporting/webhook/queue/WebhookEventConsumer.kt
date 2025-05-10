package com.aamdigital.aambackendservice.reporting.webhook.queue

import com.rabbitmq.client.Channel
import org.springframework.amqp.core.Message

interface WebhookEventConsumer {
    fun consume(rawMessage: String, message: Message, channel: Channel)
}
