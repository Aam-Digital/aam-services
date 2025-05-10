package com.aamdigital.aambackendservice.skill.core

import com.aamdigital.aambackendservice.common.domain.DomainReference
import com.aamdigital.aambackendservice.common.error.AamException
import com.aamdigital.aambackendservice.common.queue.core.QueueMessageParser
import com.aamdigital.aambackendservice.skill.core.event.UserProfileUpdateEvent
import com.aamdigital.aambackendservice.skill.di.UserProfileUpdateEventQueueConfiguration.Companion.USER_PROFILE_UPDATE_QUEUE
import com.rabbitmq.client.Channel
import org.slf4j.LoggerFactory
import org.springframework.amqp.AmqpRejectAndDontRequeueException
import org.springframework.amqp.core.Message
import org.springframework.amqp.rabbit.annotation.RabbitListener

open class DefaultUserProfileUpdateConsumer(
    private val messageParser: QueueMessageParser,
    private val syncUserProfileUseCase: SyncUserProfileUseCase,
) : UserProfileUpdateConsumer {

    private val logger = LoggerFactory.getLogger(javaClass)

    @RabbitListener(
        queues = [USER_PROFILE_UPDATE_QUEUE],
        concurrency = "1-1"
    )
    override fun consume(rawMessage: String, message: Message, channel: Channel) {
        val type = try {
            messageParser.getTypeKClass(rawMessage.toByteArray())
        } catch (ex: AamException) {
            throw AmqpRejectAndDontRequeueException("[${ex.code}] ${ex.localizedMessage}", ex)
        }

        when (type.qualifiedName) {
            UserProfileUpdateEvent::class.qualifiedName -> {
                val payload = messageParser.getPayload(
                    body = rawMessage.toByteArray(),
                    kClass = UserProfileUpdateEvent::class
                )
                try {
                    syncUserProfileUseCase.run(
                        SyncUserProfileRequest(
                            userProfile = DomainReference(payload.userProfileId),
                            project = DomainReference(payload.projectId),
                        )
                    )
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
                    "[DefaultUserProfileUpdateConsumer] Could not find any use case for this EventType: {}",
                    type.qualifiedName,
                )

                throw AmqpRejectAndDontRequeueException(
                    "[NO_USECASE_CONFIGURED] Could not found matching use case for: ${type.qualifiedName}",
                )
            }
        }
    }
}
