package com.aamdigital.aambackendservice.skill.core

import com.aamdigital.aambackendservice.skill.domain.UserProfile

interface UserProfileMatcher {

    fun findExternalUserProfiles(
        userProfile: UserProfile,
    )
}
