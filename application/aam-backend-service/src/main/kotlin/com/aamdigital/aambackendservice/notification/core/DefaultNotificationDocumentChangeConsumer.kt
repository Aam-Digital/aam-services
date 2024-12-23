package com.aamdigital.aambackendservice.notification.core

import com.aamdigital.aambackendservice.changes.domain.DocumentChangeEvent
import com.aamdigital.aambackendservice.error.AamException
import com.aamdigital.aambackendservice.notification.di.NotificationQueueConfiguration.Companion.DOCUMENT_CHANGES_NOTIFICATION_QUEUE
import com.aamdigital.aambackendservice.queue.core.QueueMessageParser
import com.rabbitmq.client.Channel
import org.slf4j.LoggerFactory
import org.springframework.amqp.AmqpRejectAndDontRequeueException
import org.springframework.amqp.core.Message
import org.springframework.amqp.rabbit.annotation.RabbitListener

class DefaultNotificationDocumentChangeConsumer(
    private val messageParser: QueueMessageParser,
    private val syncNotificationConfigUseCase: SyncNotificationConfigUseCase,
    private val applyNotificationRulesUseCase: ApplyNotificationRulesUseCase,
) : NotificationDocumentChangeConsumer {
    private val logger = LoggerFactory.getLogger(javaClass)

    @RabbitListener(
        queues = [DOCUMENT_CHANGES_NOTIFICATION_QUEUE],
        // avoid concurrent processing so that we do not trigger multiple calculations for same data unnecessarily
        concurrency = "1-1",
    )
    override fun consume(rawMessage: String, message: Message, channel: Channel) {
        val type = try {
            messageParser.getTypeKClass(rawMessage.toByteArray())
        } catch (ex: AamException) {
            throw AmqpRejectAndDontRequeueException("[${ex.code}] ${ex.localizedMessage}", ex)
        }

        when (type.qualifiedName) {
            DocumentChangeEvent::class.qualifiedName -> {
                val payload: DocumentChangeEvent = messageParser.getPayload(
                    body = rawMessage.toByteArray(),
                    kClass = DocumentChangeEvent::class
                )

                if (payload.documentId.startsWith("NotificationConfig:")) {
                    logger.trace(payload.toString())

                    syncNotificationConfigUseCase.run(
                        request = SyncNotificationConfigRequest(
                            notificationConfigDatabase = "app", // todo: configurable
                            notificationConfigId = payload.documentId,
                            notificationConfigRev = payload.rev,
                        )
                    )

                    return
                }

                applyNotificationRulesUseCase.run(
                    request = ApplyNotificationRulesRequest(
                        documentChangeEvent = payload
                    )
                )

                return
            }

            else -> {
                logger.warn(
                    "[DefaultNotificationDocumentChangeConsumer] Could not find any use case for this EventType: {}",
                    type.qualifiedName,
                )
                throw AmqpRejectAndDontRequeueException(
                    "[NO_USECASE_CONFIGURED] Could not find matching use case for: ${type.qualifiedName}",
                )
            }
        }
    }
}
