package com.aamdigital.aambackendservice.notification.queue

import com.aamdigital.aambackendservice.common.error.AamException
import com.aamdigital.aambackendservice.common.queue.core.QueueMessageParser
import com.aamdigital.aambackendservice.notification.core.CreateUserNotificationEvent
import com.aamdigital.aambackendservice.common.domain.UseCaseOutcome
import com.aamdigital.aambackendservice.notification.core.create.CreateNotificationRequest
import com.aamdigital.aambackendservice.notification.core.create.CreateNotificationUseCase
import com.aamdigital.aambackendservice.notification.di.NotificationQueueConfiguration.Companion.USER_NOTIFICATION_QUEUE
import com.rabbitmq.client.Channel
import org.slf4j.LoggerFactory
import org.springframework.amqp.AmqpRejectAndDontRequeueException
import org.springframework.amqp.core.Message
import org.springframework.amqp.rabbit.annotation.RabbitListener

class DefaultUserNotificationConsumer(
    private val messageParser: QueueMessageParser,
    private val createNotificationUseCase: CreateNotificationUseCase
) : UserNotificationConsumer {
    private val logger = LoggerFactory.getLogger(javaClass)

    @RabbitListener(
        queues = [USER_NOTIFICATION_QUEUE],
        // avoid concurrent processing so that we do not trigger multiple calculations for same data unnecessarily
        concurrency = "1-1"
    )
    override fun consume(
        rawMessage: String,
        message: Message,
        channel: Channel
    ) {
        val type =
            try {
                messageParser.getTypeKClass(rawMessage.toByteArray())
            } catch (ex: AamException) {
                throw AmqpRejectAndDontRequeueException("[${ex.code}] ${ex.localizedMessage}", ex)
            }

        when (type.qualifiedName) {
            CreateUserNotificationEvent::class.qualifiedName -> {
                val payload: CreateUserNotificationEvent =
                    messageParser.getPayload(
                        body = rawMessage.toByteArray(),
                        kClass = CreateUserNotificationEvent::class
                    )

                val result = createNotificationUseCase.run(
                    request =
                        CreateNotificationRequest(
                            createUserNotificationEvent = payload
                        )
                )

                when (result) {
                    is UseCaseOutcome.Failure -> {
                        logger.warn(
                            "CreateNotification failed for user={}, channel={}: [{}] {}",
                            payload.userIdentifier,
                            payload.notificationChannelType,
                            result.errorCode,
                            result.errorMessage,
                            result.cause
                        )
                    }
                    is UseCaseOutcome.Success -> {
                        logger.trace(
                            "CreateNotification completed for user={}, channel={}",
                            payload.userIdentifier,
                            payload.notificationChannelType
                        )
                    }
                }

                return
            }

            else -> {
                logger.warn(
                    "[DefaultUserNotificationConsumer] Could not find any use case for this EventType: {}",
                    type.qualifiedName
                )
                throw AmqpRejectAndDontRequeueException(
                    "[NO_USECASE_CONFIGURED] Could not find matching use case for: ${type.qualifiedName}"
                )
            }
        }
    }
}
