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
     * Left `null`, the use case derives the cursor from the profiles already stored and runs a
     * delta sync, so no sync cursor has to be persisted anywhere. Set [fullSync] to request a full
     * sync; it takes precedence over this value.
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
