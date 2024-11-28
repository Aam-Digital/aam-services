package com.aamdigital.aambackendservice.skill.core

import com.aamdigital.aambackendservice.domain.DomainUseCase
import com.aamdigital.aambackendservice.domain.UseCaseData
import com.aamdigital.aambackendservice.domain.UseCaseRequest
import com.aamdigital.aambackendservice.skill.domain.UserProfile

data class SearchUserProfileRequest(
    val fullName: String?,
    val email: String?,
    val phone: String?,
) : UseCaseRequest

data class SearchUserProfileData(
    val result: List<UserProfile>
) : UseCaseData

abstract class SearchUserProfileUseCase :
    DomainUseCase<SearchUserProfileRequest, SearchUserProfileData>()
