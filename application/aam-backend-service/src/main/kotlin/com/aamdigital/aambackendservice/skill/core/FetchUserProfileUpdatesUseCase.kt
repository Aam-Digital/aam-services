package com.aamdigital.aambackendservice.skill.core

import com.aamdigital.aambackendservice.domain.DomainReference
import com.aamdigital.aambackendservice.domain.DomainUseCase
import com.aamdigital.aambackendservice.domain.UseCaseData
import com.aamdigital.aambackendservice.domain.UseCaseRequest

data class FetchUserProfileUpdatesRequest(
    val projectId: String,
) : UseCaseRequest

/**
 * result will be a list of user profiles with updates available.
 */
data class FetchUserProfileUpdatesData(
    val result: List<DomainReference>
) : UseCaseData

abstract class FetchUserProfileUpdatesUseCase :
    DomainUseCase<FetchUserProfileUpdatesRequest, FetchUserProfileUpdatesData>()
