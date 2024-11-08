package com.aamdigital.aambackendservice.skill.core

import com.rabbitmq.client.Channel
import org.springframework.amqp.core.Message

interface UserProfileUpdateConsumer {
    fun consume(rawMessage: String, message: Message, channel: Channel)
}
