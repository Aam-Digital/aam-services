package com.aamdigital.aambackendservice.notification.core

import com.aamdigital.aambackendservice.error.AamErrorCode
import com.aamdigital.aambackendservice.error.InternalServerException
import com.aamdigital.aambackendservice.notification.core.event.CreateUserNotificationEvent
import com.aamdigital.aambackendservice.queue.core.QueueMessage
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.amqp.AmqpException
import org.springframework.amqp.rabbit.core.RabbitTemplate
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.*

class DefaultUserNotificationPublisher(
    private val objectMapper: ObjectMapper,
    private val rabbitTemplate: RabbitTemplate,
) : UserNotificationPublisher {

    enum class DefaultNotificationPublisherErrorCode : AamErrorCode {
        EVENT_PUBLISH_ERROR
    }

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun publish(channel: String, event: CreateUserNotificationEvent): QueueMessage {
        val message = QueueMessage(
            id = UUID.randomUUID(),
            eventType = CreateUserNotificationEvent::class.java.canonicalName,
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
                message = "Could not publish SendNotificationEvent: $event",
                code = DefaultNotificationPublisherErrorCode.EVENT_PUBLISH_ERROR,
                cause = ex
            )
        }

        logger.trace(
            "[DefaultNotificationPublisher]: publish message to channel '{}' Payload: {}",
            channel,
            objectMapper.writeValueAsString(message)
        )

        return message
    }
}
