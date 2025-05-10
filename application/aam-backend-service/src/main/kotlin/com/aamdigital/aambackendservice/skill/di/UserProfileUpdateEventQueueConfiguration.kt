package com.aamdigital.aambackendservice.skill.di

import com.aamdigital.aambackendservice.common.queue.core.QueueMessageParser
import com.aamdigital.aambackendservice.skill.core.DefaultUserProfileUpdateConsumer
import com.aamdigital.aambackendservice.skill.core.DefaultUserProfileUpdatePublisher
import com.aamdigital.aambackendservice.skill.core.SyncUserProfileUseCase
import com.aamdigital.aambackendservice.skill.core.UserProfileUpdateConsumer
import com.aamdigital.aambackendservice.skill.core.UserProfileUpdatePublisher
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.amqp.core.Queue
import org.springframework.amqp.core.QueueBuilder
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnProperty(
    prefix = "features.skill-api",
    name = ["mode"],
    havingValue = "skilllab",
    matchIfMissing = false
)
class UserProfileUpdateEventQueueConfiguration {

    companion object {
        const val USER_PROFILE_UPDATE_QUEUE = "skill.userProfile.update"
    }

    @Bean("user-profile-update-queue")
    fun userProfileUpdateQueue(): Queue = QueueBuilder
        .durable(USER_PROFILE_UPDATE_QUEUE)
        .build()

    @Bean
    fun defaultUserProfileUpdatePublisher(
        objectMapper: ObjectMapper,
        rabbitTemplate: RabbitTemplate,
    ): UserProfileUpdatePublisher = DefaultUserProfileUpdatePublisher(
        objectMapper = objectMapper,
        rabbitTemplate = rabbitTemplate,
    )

    @Bean
    fun defaultUserProfileUpdateConsumer(
        messageParser: QueueMessageParser,
        syncUserProfileUseCase: SyncUserProfileUseCase,
    ): UserProfileUpdateConsumer = DefaultUserProfileUpdateConsumer(messageParser, syncUserProfileUseCase)
}
