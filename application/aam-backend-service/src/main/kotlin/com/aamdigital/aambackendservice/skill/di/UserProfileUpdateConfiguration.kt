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
class UserProfileUpdateConfiguration {
    @Bean
    fun inProcessUserProfileUpdatePublisher(
        syncUserProfileUseCase: SyncUserProfileUseCase
    ): UserProfileUpdatePublisher = InProcessUserProfileUpdatePublisher(syncUserProfileUseCase)
}
