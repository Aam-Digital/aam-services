package com.aamdigital.aambackendservice.reporting.changes.queue

import com.aamdigital.aambackendservice.error.AamException
import com.aamdigital.aambackendservice.error.InternalServerException
import com.aamdigital.aambackendservice.queue.core.QueueMessage
import com.aamdigital.aambackendservice.reporting.changes.core.ChangeEventPublisher
import com.aamdigital.aambackendservice.reporting.domain.event.DatabaseChangeEvent
import com.aamdigital.aambackendservice.reporting.domain.event.DocumentChangeEvent
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.amqp.AmqpException
import org.springframework.amqp.rabbit.core.RabbitTemplate
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.*

class DefaultChangeEventPublisher(
    private val objectMapper: ObjectMapper,
    private val rabbitTemplate: RabbitTemplate,
) : ChangeEventPublisher {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Throws(AamException::class)
    override fun publish(channel: String, event: DatabaseChangeEvent): QueueMessage {
        val message = QueueMessage(
            id = UUID.randomUUID(),
            type = DatabaseChangeEvent::class.java.canonicalName,
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
                message = "Could not publish DatabaseChangeEvent: $event",
                code = "EVENT_PUBLISH_ERROR",
                cause = ex
            )
        }

        logger.trace(
            "[DefaultDatabaseChangeEventPublisher]: publish message to channel '{}' Payload: {}",
            channel,
            objectMapper.writeValueAsString(message)
        )
        return message
    }

    @Throws(AamException::class)
    override fun publish(exchange: String, event: DocumentChangeEvent): QueueMessage {
        val message = QueueMessage(
            id = UUID.randomUUID(),
            type = DocumentChangeEvent::class.java.canonicalName,
            payload = event,
            createdAt = Instant.now()
                .atOffset(ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        )
        try {
            rabbitTemplate.convertAndSend(
                exchange,
                "",
                objectMapper.writeValueAsString(message)
            )
        } catch (ex: AmqpException) {
            throw InternalServerException(
                message = "Could not publish DocumentChangeEvent: $event",
                code = "EVENT_PUBLISH_ERROR",
                cause = ex
            )
        }

        logger.trace(
            "[DefaultDocumentChangeEventPublisher]: publish message to channel '{}' Payload: {}",
            exchange,
            objectMapper.writeValueAsString(message)
        )
        return message
    }
}
