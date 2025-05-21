package com.aamdigital.aambackendservice.reporting.webhook.queue

import com.aamdigital.aambackendservice.common.error.AamException
import com.aamdigital.aambackendservice.common.queue.core.QueueMessageParser
import com.aamdigital.aambackendservice.reporting.webhook.WebhookEvent
import com.aamdigital.aambackendservice.reporting.webhook.core.TriggerWebhookUseCase
import com.aamdigital.aambackendservice.reporting.webhook.di.ReportingNotificationQueueConfiguration.Companion.NOTIFICATION_QUEUE
import com.rabbitmq.client.Channel
import org.slf4j.LoggerFactory
import org.springframework.amqp.AmqpRejectAndDontRequeueException
import org.springframework.amqp.core.Message
import org.springframework.amqp.rabbit.annotation.RabbitListener

class WebhookEventConsumer(
    private val messageParser: QueueMessageParser,
    private val useCase: TriggerWebhookUseCase,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @RabbitListener(
        queues = [NOTIFICATION_QUEUE],
    )
    fun consume(rawMessage: String, message: Message, channel: Channel) {
        val type = try {
            messageParser.getTypeKClass(rawMessage.toByteArray())
        } catch (ex: AamException) {
            throw AmqpRejectAndDontRequeueException("[${ex.code}] ${ex.localizedMessage}", ex)
        }

        when (type.qualifiedName) {
            WebhookEvent::class.qualifiedName -> {
                val payload = messageParser.getPayload(
                    body = rawMessage.toByteArray(),
                    kClass = WebhookEvent::class
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
                    "Could not find any use case for this EventType: {}",
                    type.qualifiedName,
                )

                throw AmqpRejectAndDontRequeueException(
                    "[NO_USECASE_CONFIGURED] Could not found matching use case for: ${type.qualifiedName}",
                )
            }
        }
    }
}
