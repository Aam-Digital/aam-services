package com.aamdigital.aambackendservice.skill.core

import com.aamdigital.aambackendservice.common.domain.DomainUseCase
import com.aamdigital.aambackendservice.common.domain.UseCaseData
import com.aamdigital.aambackendservice.common.domain.UseCaseRequest
import com.aamdigital.aambackendservice.skill.domain.UserProfile

data class SearchUserProfileRequest(
    val fullName: String?,
    val email: String?,
    val phone: String?,
    val page: Int,
    val pageSize: Int,
) : UseCaseRequest

data class SearchUserProfileData(
    val result: List<UserProfile>,
    val totalElements: Int,
    val totalPages: Int,
) : UseCaseData

abstract class SearchUserProfileUseCase :
    DomainUseCase<SearchUserProfileRequest, SearchUserProfileData>()
