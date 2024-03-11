package com.aamdigital.aambackendservice.notification.core

import com.aamdigital.aambackendservice.error.AamException
import com.aamdigital.aambackendservice.error.InternalServerException
import com.aamdigital.aambackendservice.notification.core.event.NotificationEvent
import com.aamdigital.aambackendservice.queue.core.QueueMessage
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.amqp.AmqpException
import org.springframework.amqp.rabbit.core.RabbitTemplate
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.*

class DefaultNotificationEventPublisher(
    private val objectMapper: ObjectMapper,
    private val rabbitTemplate: RabbitTemplate,
) : NotificationEventPublisher {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Throws(AamException::class)
    override fun publish(channel: String, event: NotificationEvent): QueueMessage {
        val message = QueueMessage(
            id = UUID.randomUUID(),
            type = NotificationEvent::class.java.canonicalName,
            payload = event,
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
                message = "Could not publish NotificationEvent: $event",
                code = "EVENT_PUBLISH_ERROR",
                cause = ex
            )
        }

        logger.trace(
            "[DefaultNotificationEventPublisher]: publish message to channel '{}' Payload: {}",
            channel,
            jacksonObjectMapper().writeValueAsString(message)
        )

        return message
    }
}
