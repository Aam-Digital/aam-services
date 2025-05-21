package com.aamdigital.aambackendservice.reporting.webhook.queue

import com.aamdigital.aambackendservice.common.error.AamErrorCode
import com.aamdigital.aambackendservice.common.error.AamException
import com.aamdigital.aambackendservice.common.error.InternalServerException
import com.aamdigital.aambackendservice.common.queue.core.QueueMessage
import com.aamdigital.aambackendservice.reporting.webhook.WebhookEvent
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.amqp.AmqpException
import org.springframework.amqp.rabbit.core.RabbitTemplate
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.*

class WebhookEventPublisher(
    private val objectMapper: ObjectMapper,
    private val rabbitTemplate: RabbitTemplate,
) {

    enum class NotificationEventPublisherErrorCode : AamErrorCode {
        EVENT_PUBLISH_ERROR
    }

    private val logger = LoggerFactory.getLogger(javaClass)

    @Throws(AamException::class)
    fun publish(channel: String, event: WebhookEvent): QueueMessage {
        val message = QueueMessage(
            id = UUID.randomUUID(),
            eventType = WebhookEvent::class.java.canonicalName,
            event = event,
            createdAt = Instant.now()
                .atOffset(ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        )

        try {
            rabbitTemplate.convertAndSend(
                channel,
                objectMapper.writeValueAsString(message)
            )
        } catch (ex: AmqpException) {
            throw InternalServerException(
                message = "Could not publish NotificationEvent: $event",
                code = NotificationEventPublisherErrorCode.EVENT_PUBLISH_ERROR,
                cause = ex
            )
        }

        logger.trace(
            "publish message to channel '{}' Payload: {}",
            channel,
            objectMapper.writeValueAsString(message)
        )

        return message
    }
}
