package com.aamdigital.aambackendservice.skill.skilllab

import com.aamdigital.aambackendservice.skill.core.UserProfileMatcher
import com.aamdigital.aambackendservice.skill.domain.UserProfile
import org.springframework.web.client.RestClient

class SkillLabUserProfileMatcher(
    val http: RestClient
) : UserProfileMatcher {
    override fun findExternalUserProfiles(userProfile: UserProfile) {
        TODO("Not yet implemented")
    }
}
