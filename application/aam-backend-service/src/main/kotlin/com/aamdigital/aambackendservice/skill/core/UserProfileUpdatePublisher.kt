package com.aamdigital.aambackendservice.skill.core

import com.aamdigital.aambackendservice.skill.core.event.UserProfileUpdateEvent

/**
 * Handles a SkillLab user profile that has changed upstream.
 *
 * See [InProcessUserProfileUpdatePublisher] for the only implementation.
 */
interface UserProfileUpdatePublisher {
    fun publish(event: UserProfileUpdateEvent)
}
