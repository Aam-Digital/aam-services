package com.aamdigital.aambackendservice.skill.core

import com.aamdigital.aambackendservice.common.error.AamErrorCode
import com.aamdigital.aambackendservice.common.error.AamException
import com.aamdigital.aambackendservice.common.error.InternalServerException
import com.aamdigital.aambackendservice.common.queue.core.QueueMessage
import com.aamdigital.aambackendservice.skill.core.event.UserProfileUpdateEvent
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.amqp.AmqpException
import org.springframework.amqp.rabbit.core.RabbitTemplate
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.*

class DefaultUserProfileUpdatePublisher(
    private val objectMapper: ObjectMapper,
    private val rabbitTemplate: RabbitTemplate,
) : UserProfileUpdatePublisher {

    enum class DefaultUserProfileUpdatePublisherErrorCode : AamErrorCode {
        EVENT_PUBLISH_ERROR
    }

    private val logger = LoggerFactory.getLogger(javaClass)

    @Throws(AamException::class)
    override fun publish(channel: String, event: UserProfileUpdateEvent): QueueMessage {
        val message = QueueMessage(
            id = UUID.randomUUID(),
            eventType = UserProfileUpdateEvent::class.java.canonicalName,
            event = event,
            createdAt = Instant.now()
                .atOffset(ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        )

        try {
            rabbitTemplate.convertAndSend(
                channel,
                objectMapper.writeValueAsString(message)
            )
        } catch (ex: AmqpException) {
            throw InternalServerException(
                message = "Could not publish UserProfileUpdateEvent: $event",
                code = DefaultUserProfileUpdatePublisherErrorCode.EVENT_PUBLISH_ERROR,
                cause = ex
            )
        }

        logger.trace(
            "[DefaultNotificationEventPublisher]: publish message to channel '{}' Payload: {}",
            channel,
            objectMapper.writeValueAsString(message)
        )

        return message
    }
}
