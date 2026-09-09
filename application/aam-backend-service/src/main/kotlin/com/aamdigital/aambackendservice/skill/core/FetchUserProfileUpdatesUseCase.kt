package com.aamdigital.aambackendservice.skill.core

import com.aamdigital.aambackendservice.common.domain.DomainReference
import com.aamdigital.aambackendservice.common.domain.DomainUseCase
import com.aamdigital.aambackendservice.common.domain.UseCaseData
import com.aamdigital.aambackendservice.common.domain.UseCaseRequest
import java.time.Instant

data class FetchUserProfileUpdatesRequest(
    val projectId: String,
    /**
     * Only fetch profiles the external system changed at or after this point.
     *
     * `null` requests a full sync. Left unset, the use case derives it from the profiles already
     * stored, so no sync cursor has to be persisted anywhere.
     */
    val updatedFrom: Instant? = null,
    val fullSync: Boolean = false
) : UseCaseRequest

/**
 * result will be a list of user profiles with updates available.
 */
data class FetchUserProfileUpdatesData(
    val result: List<DomainReference>
) : UseCaseData

abstract class FetchUserProfileUpdatesUseCase :
    DomainUseCase<FetchUserProfileUpdatesRequest, FetchUserProfileUpdatesData>()
