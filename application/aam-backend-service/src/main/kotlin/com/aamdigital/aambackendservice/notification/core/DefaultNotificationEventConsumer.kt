package com.aamdigital.aambackendservice.notification.core

import com.aamdigital.aambackendservice.error.AamException
import com.aamdigital.aambackendservice.notification.core.event.NotificationEvent
import com.aamdigital.aambackendservice.notification.di.NotificationQueueConfiguration
import com.aamdigital.aambackendservice.queue.core.QueueMessageParser
import com.rabbitmq.client.Channel
import org.slf4j.LoggerFactory
import org.springframework.amqp.AmqpRejectAndDontRequeueException
import org.springframework.amqp.core.Message
import org.springframework.amqp.rabbit.annotation.RabbitListener
import reactor.core.publisher.Mono

class DefaultNotificationEventConsumer(
    private val messageParser: QueueMessageParser,
    private val useCase: TriggerWebhookUseCase,
) : NotificationEventConsumer {

    private val logger = LoggerFactory.getLogger(javaClass)

    @RabbitListener(
        queues = [NotificationQueueConfiguration.NOTIFICATION_QUEUE],
        ackMode = "MANUAL"
    )
    override fun consume(rawMessage: String, message: Message, channel: Channel): Mono<Unit> {

        val type = try {
            messageParser.getTypeKClass(rawMessage.toByteArray())
        } catch (ex: AamException) {
            return Mono.error { throw AmqpRejectAndDontRequeueException("[${ex.code}] ${ex.localizedMessage}", ex) }
        }
        when (type.qualifiedName) {
            NotificationEvent::class.qualifiedName -> {
                val payload = messageParser.getPayload(
                    body = rawMessage.toByteArray(),
                    kClass = NotificationEvent::class
                )

                logger.debug("Payload parsed: {}", payload)

                return useCase.trigger(payload).flatMap {
                    Mono.empty()
                }
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
