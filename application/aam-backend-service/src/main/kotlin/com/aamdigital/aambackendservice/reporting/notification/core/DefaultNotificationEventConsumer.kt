package com.aamdigital.aambackendservice.reporting.notification.core

import com.aamdigital.aambackendservice.error.AamException
import com.aamdigital.aambackendservice.queue.core.QueueMessageParser
import com.aamdigital.aambackendservice.reporting.domain.event.NotificationEvent
import com.aamdigital.aambackendservice.reporting.notification.di.NotificationQueueConfiguration.Companion.NOTIFICATION_QUEUE
import com.rabbitmq.client.Channel
import org.slf4j.LoggerFactory
import org.springframework.amqp.AmqpRejectAndDontRequeueException
import org.springframework.amqp.core.Message
import org.springframework.amqp.rabbit.annotation.RabbitListener

class DefaultNotificationEventConsumer(
    private val messageParser: QueueMessageParser,
    private val useCase: TriggerWebhookUseCase,
) : NotificationEventConsumer {

    private val logger = LoggerFactory.getLogger(javaClass)

    @RabbitListener(
        queues = [NOTIFICATION_QUEUE],
    )
    override fun consume(rawMessage: String, message: Message, channel: Channel) {
        val type = try {
            messageParser.getTypeKClass(rawMessage.toByteArray())
        } catch (ex: AamException) {
            throw AmqpRejectAndDontRequeueException("[${ex.code}] ${ex.localizedMessage}", ex)
        }

        when (type.qualifiedName) {
            NotificationEvent::class.qualifiedName -> {
                val payload = messageParser.getPayload(
                    body = rawMessage.toByteArray(),
                    kClass = NotificationEvent::class
                )
                try {
                    useCase.trigger(payload)
                } catch (ex: Exception) {
                    throw AmqpRejectAndDontRequeueException(
                        "[USECASE_ERROR] ${ex.localizedMessage}",
                        ex
                    )
                }
                return
            }

            else -> {
                logger.warn(
                    "[DefaultNotificationEventConsumer] Could not find any use case for this EventType: {}",
                    type.qualifiedName,
                )

                throw AmqpRejectAndDontRequeueException(
                    "[NO_USECASE_CONFIGURED] Could not found matching use case for: ${type.qualifiedName}",
                )
            }
        }
    }
}
