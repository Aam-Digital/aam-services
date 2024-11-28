package com.aamdigital.aambackendservice.skill.core

import com.aamdigital.aambackendservice.domain.DomainReference
import com.aamdigital.aambackendservice.error.AamException
import com.aamdigital.aambackendservice.skill.domain.UserProfile
import org.springframework.data.domain.Pageable

interface UserProfileStorage {

    @Throws(AamException::class)
    fun fetchUserProfile(externalIdentifier: DomainReference): UserProfile

    @Throws(AamException::class)
    fun fetchUserProfiles(pageable: Pageable, updatedFrom: String?): List<DomainReference>
}
