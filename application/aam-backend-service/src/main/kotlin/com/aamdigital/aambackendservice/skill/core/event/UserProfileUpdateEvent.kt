package com.aamdigital.aambackendservice.skill.core.event

import com.aamdigital.aambackendservice.events.DomainEvent

data class UserProfileUpdateEvent(
    val projectId: String,
    val userProfileId: String,
) : DomainEvent()
