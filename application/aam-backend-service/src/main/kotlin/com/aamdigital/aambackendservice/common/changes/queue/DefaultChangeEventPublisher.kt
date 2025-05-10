package com.aamdigital.aambackendservice.common.changes.queue

import com.aamdigital.aambackendservice.common.changes.core.ChangeEventPublisher
import com.aamdigital.aambackendservice.common.changes.domain.DatabaseChangeEvent
import com.aamdigital.aambackendservice.common.changes.domain.DocumentChangeEvent
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

class DefaultChangeEventPublisher(
    private val objectMapper: ObjectMapper,
    private val rabbitTemplate: RabbitTemplate,
) : ChangeEventPublisher {

    enum class DefaultChangeEventPublisherErrorCode : AamErrorCode {
        EVENT_PUBLISH_ERROR
    }

    private val logger = LoggerFactory.getLogger(javaClass)

    @Throws(AamException::class)
    override fun publish(channel: String, event: DatabaseChangeEvent): QueueMessage {
        val message = QueueMessage(
            id = UUID.randomUUID(),
            eventType = DatabaseChangeEvent::class.java.canonicalName,
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
                message = "Could not publish DatabaseChangeEvent: $event",
                code = DefaultChangeEventPublisherErrorCode.EVENT_PUBLISH_ERROR,
                cause = ex
            )
        }
        return message
    }

    @Throws(AamException::class)
    override fun publish(exchange: String, event: DocumentChangeEvent): QueueMessage {
        val message = QueueMessage(
            id = UUID.randomUUID(),
            eventType = DocumentChangeEvent::class.java.canonicalName,
            event = event,
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
                code = DefaultChangeEventPublisherErrorCode.EVENT_PUBLISH_ERROR,
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
