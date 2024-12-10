package com.aamdigital.aambackendservice.skill.core

import com.aamdigital.aambackendservice.domain.DomainReference
import com.aamdigital.aambackendservice.domain.DomainUseCase
import com.aamdigital.aambackendservice.domain.UseCaseData
import com.aamdigital.aambackendservice.domain.UseCaseRequest
import com.aamdigital.aambackendservice.skill.domain.UserProfile

data class SyncUserProfileRequest(
    val userProfile: DomainReference,
    val project: DomainReference,
) : UseCaseRequest

data class SyncUserProfileData(
    val result: UserProfile
) : UseCaseData

abstract class SyncUserProfileUseCase :
    DomainUseCase<SyncUserProfileRequest, SyncUserProfileData>()
