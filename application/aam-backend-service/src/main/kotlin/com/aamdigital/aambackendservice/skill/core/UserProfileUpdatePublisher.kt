package com.aamdigital.aambackendservice.skill.core

import com.aamdigital.aambackendservice.queue.core.QueueMessage
import com.aamdigital.aambackendservice.skill.core.event.UserProfileUpdateEvent

interface UserProfileUpdatePublisher {
    fun publish(channel: String, event: UserProfileUpdateEvent): QueueMessage
}
