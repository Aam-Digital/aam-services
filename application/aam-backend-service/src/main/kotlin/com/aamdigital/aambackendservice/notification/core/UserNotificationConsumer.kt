package com.aamdigital.aambackendservice.notification.core

import com.rabbitmq.client.Channel
import org.springframework.amqp.core.Message

interface UserNotificationConsumer {
    fun consume(rawMessage: String, message: Message, channel: Channel)
}
