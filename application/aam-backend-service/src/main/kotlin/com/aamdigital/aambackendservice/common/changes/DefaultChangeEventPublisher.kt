package com.aamdigital.aambackendservice.common.changes

import com.aamdigital.aambackendservice.common.error.AamErrorCode
import com.aamdigital.aambackendservice.common.error.AamException
import com.aamdigital.aambackendservice.common.error.InternalServerException
import com.aamdigital.aambackendservice.common.queue.core.QueueMessage
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.amqp.AmqpException
import org.springframework.amqp.rabbit.core.RabbitTemplate
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.*

/**
 * RabbitMQ implementation of [ChangeEventPublisher].
 * Wraps each [DocumentChangeEvent] in a [QueueMessage] envelope and sends it
 * to the specified fanout exchange.
 */
class DefaultChangeEventPublisher(
    private val objectMapper: ObjectMapper,
    private val rabbitTemplate: RabbitTemplate
) : ChangeEventPublisher {
    enum class DefaultChangeEventPublisherErrorCode : AamErrorCode {
        EVENT_PUBLISH_ERROR
    }

    private val logger = LoggerFactory.getLogger(javaClass)

    @Throws(AamException::class)
    override fun publish(
        exchange: String,
        event: DocumentChangeEvent
    ): QueueMessage {
        val message =
            QueueMessage(
                id = UUID.randomUUID(),
                eventType = DocumentChangeEvent::class.java.canonicalName,
                event = event,
                createdAt =
                    Instant
                        .now()
                        .atOffset(ZoneOffset.UTC)
                        .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            )
        val payload = objectMapper.writeValueAsString(message)
        try {
            rabbitTemplate.convertAndSend(
                exchange,
                "",
                payload
            )
        } catch (ex: AmqpException) {
            throw InternalServerException(
                message = "Could not publish DocumentChangeEvent: $event",
                code = DefaultChangeEventPublisherErrorCode.EVENT_PUBLISH_ERROR,
                cause = ex
            )
        }

        logger.trace(
            "[DefaultChangeEventPublisher]: publish message to exchange '{}' Payload: {}",
            exchange,
            payload
        )
        return message
    }
}
