package com.aamdigital.aambackendservice.skill.core.event

data class UserProfileUpdateEvent(
    val projectId: String,
    val userProfileId: String
)
