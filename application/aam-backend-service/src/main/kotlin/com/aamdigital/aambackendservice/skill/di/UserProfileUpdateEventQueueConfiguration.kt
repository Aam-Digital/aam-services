package com.aamdigital.aambackendservice.skill.di

import com.aamdigital.aambackendservice.skill.ConditionalOnSkillApiEnabled
import com.aamdigital.aambackendservice.skill.ConditionalOnSkillLabMode
import com.aamdigital.aambackendservice.skill.core.InProcessUserProfileUpdatePublisher
import com.aamdigital.aambackendservice.skill.core.SyncUserProfileUseCase
import com.aamdigital.aambackendservice.skill.core.UserProfileUpdatePublisher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnSkillApiEnabled
@ConditionalOnSkillLabMode
class UserProfileUpdateEventQueueConfiguration {
    companion object {
        /**
         * Kept as the [UserProfileUpdatePublisher.publish] channel name: user profile updates are
         * handled in process now, so no queue is declared for it, but the interface still carries
         * the queue-shaped signature. Both go away with RabbitMQ.
         */
        const val USER_PROFILE_UPDATE_QUEUE = "skill.userProfile.update"
    }

    @Bean
    fun inProcessUserProfileUpdatePublisher(
        syncUserProfileUseCase: SyncUserProfileUseCase
    ): UserProfileUpdatePublisher = InProcessUserProfileUpdatePublisher(syncUserProfileUseCase)
}
