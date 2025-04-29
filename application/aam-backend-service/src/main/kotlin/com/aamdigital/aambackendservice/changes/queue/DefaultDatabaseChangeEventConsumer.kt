package com.aamdigital.aambackendservice.changes.queue

import com.aamdigital.aambackendservice.changes.core.CreateDocumentChangeUseCase
import com.aamdigital.aambackendservice.changes.core.DatabaseChangeEventConsumer
import com.aamdigital.aambackendservice.changes.di.ChangesQueueConfiguration.Companion.DB_CHANGES_QUEUE
import com.aamdigital.aambackendservice.changes.domain.DatabaseChangeEvent
import com.aamdigital.aambackendservice.error.AamException
import com.aamdigital.aambackendservice.queue.core.QueueMessageParser
import com.rabbitmq.client.Channel
import org.slf4j.LoggerFactory
import org.springframework.amqp.AmqpRejectAndDontRequeueException
import org.springframework.amqp.core.Message
import org.springframework.amqp.rabbit.annotation.RabbitListener

class DefaultDatabaseChangeEventConsumer(
    private val messageParser: QueueMessageParser,
    private val useCase: CreateDocumentChangeUseCase,
) : DatabaseChangeEventConsumer {

    private val logger = LoggerFactory.getLogger(javaClass)

    @RabbitListener(
        queues = [DB_CHANGES_QUEUE],
    )
    override fun consume(rawMessage: String, message: Message, channel: Channel) {
        val type = try {
            messageParser.getTypeKClass(rawMessage.toByteArray())
        } catch (ex: AamException) {
            throw AmqpRejectAndDontRequeueException("[${ex.code}] ${ex.localizedMessage}", ex)
        }

        when (type.qualifiedName) {
            DatabaseChangeEvent::class.qualifiedName -> {
                val payload = messageParser.getPayload(
                    body = rawMessage.toByteArray(),
                    kClass = DatabaseChangeEvent::class
                )
                return useCase.createEvent(payload)
            }

            else -> {
                logger.warn(
                    "[DefaultDocumentChangeEventProcessor] Could not find any use case for this EventType: {}",
                    type.qualifiedName,
                )

                throw AmqpRejectAndDontRequeueException(
                    "[NO_USECASE_CONFIGURED] Could not found matching use case for: ${type.qualifiedName}",
                )
            }
        }
    }
}
