package com.aamdigital.aambackendservice.notification.queue

import com.rabbitmq.client.Channel
import org.springframework.amqp.core.Message

interface UserNotificationConsumer {
    fun consume(rawMessage: String, message: Message, channel: Channel)
}
