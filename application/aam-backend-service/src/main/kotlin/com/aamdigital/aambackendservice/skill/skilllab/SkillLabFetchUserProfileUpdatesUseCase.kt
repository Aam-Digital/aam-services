package com.aamdigital.aambackendservice.skill.skilllab

import com.aamdigital.aambackendservice.common.domain.DomainReference
import com.aamdigital.aambackendservice.common.domain.UseCaseOutcome
import com.aamdigital.aambackendservice.common.error.AamErrorCode
import com.aamdigital.aambackendservice.common.error.AamException
import com.aamdigital.aambackendservice.skill.core.FetchUserProfileUpdatesData
import com.aamdigital.aambackendservice.skill.core.FetchUserProfileUpdatesRequest
import com.aamdigital.aambackendservice.skill.core.FetchUserProfileUpdatesUseCase
import com.aamdigital.aambackendservice.skill.core.SyncUserProfileRequest
import com.aamdigital.aambackendservice.skill.core.SyncUserProfileUseCase
import com.aamdigital.aambackendservice.skill.repository.SkillLabUserProfileSyncEntity
import com.aamdigital.aambackendservice.skill.repository.SkillLabUserProfileSyncRepository
import org.springframework.data.domain.Pageable
import java.time.Instant
import java.time.ZoneOffset
import kotlin.jvm.optionals.getOrNull

enum class SkillLabFetchUserProfileUpdatesErrorCode : AamErrorCode {
    EXTERNAL_SYSTEM_ERROR
}

/**
 * Fetches the user profiles that changed in this SkillLab tenant since the last sync, and syncs each
 * one with [SyncUserProfileUseCase].
 *
 * A profile that fails to sync is logged and skipped, never retried, and the sync cursor advances
 * regardless. Failing the whole fetch instead would hold the cursor back and turn one bad profile
 * into a stalled project-wide sync.
 *
 * Known gap: the scheduled sync does **not** recover such a profile. It only fetches profiles
 * changed since the cursor, so a skipped profile is synced again only once it changes in SkillLab
 * again, or when an admin triggers a `FULL` sync (or a `DELTA` sync with an earlier `updatedFrom`)
 * through `POST /v1/skill/sync/{projectId}`. Also, a sync that reaches [MAX_RESULTS_LIMIT] stops
 * fetching, but the cursor still advances past the profiles it did not fetch. See
 * `docs/modules/skill.md`.
 */
class SkillLabFetchUserProfileUpdatesUseCase(
    private val skillLabClient: SkillLabClient,
    private val skillLabUserProfileSyncRepository: SkillLabUserProfileSyncRepository,
    private val syncUserProfileUseCase: SyncUserProfileUseCase
) : FetchUserProfileUpdatesUseCase() {
    companion object {
        private const val PAGE_SIZE = 50
        private const val MAX_RESULTS_LIMIT = 10_000
    }

    override fun apply(request: FetchUserProfileUpdatesRequest): UseCaseOutcome<FetchUserProfileUpdatesData> {
        // taken before fetching, so a profile that changes while this sync runs is fetched again by
        // the next one instead of falling between the two
        val syncStartedAt = Instant.now().atOffset(ZoneOffset.UTC)
        val results = mutableListOf<DomainReference>()
        var currentSync = skillLabUserProfileSyncRepository.findByProjectId(request.projectId).getOrNull()
        var page = 1

        do {
            val batch =
                try {
                    fetchNextBatch(
                        pageable = Pageable.ofSize(PAGE_SIZE).withPage(page++),
                        currentSync = currentSync
                    )
                } catch (ex: AamException) {
                    return UseCaseOutcome.Failure(
                        errorCode = SkillLabFetchUserProfileUpdatesErrorCode.EXTERNAL_SYSTEM_ERROR,
                        errorMessage = ex.localizedMessage,
                        cause = ex
                    )
                }
            results.addAll(batch)
        } while (batch.size >= PAGE_SIZE && results.size < MAX_RESULTS_LIMIT)

        results.forEach { userProfile -> syncUserProfile(request.projectId, userProfile) }

        if (currentSync != null) {
            currentSync.latestSync = syncStartedAt
        } else {
            currentSync =
                SkillLabUserProfileSyncEntity(
                    projectId = request.projectId,
                    latestSync = syncStartedAt
                )
        }

        skillLabUserProfileSyncRepository.save(currentSync)

        return UseCaseOutcome.Success(
            data =
                FetchUserProfileUpdatesData(
                    result =
                        results.map {
                            DomainReference(id = it.id)
                        }
                )
        )
    }

    private fun syncUserProfile(
        projectId: String,
        userProfile: DomainReference
    ) {
        val outcome =
            syncUserProfileUseCase.run(
                SyncUserProfileRequest(userProfile = userProfile, project = DomainReference(projectId))
            )

        if (outcome is UseCaseOutcome.Failure) {
            logger.warn(
                "[{}] could not sync user profile {} of project {}: {}",
                outcome.errorCode,
                userProfile.id,
                projectId,
                outcome.errorMessage,
                outcome.cause
            )
        }
    }

    private fun fetchNextBatch(
        pageable: Pageable,
        currentSync: SkillLabUserProfileSyncEntity?
    ): List<DomainReference> =
        if (currentSync == null) {
            skillLabClient.fetchUserProfiles(
                pageable = pageable,
                updatedFrom = null
            )
        } else {
            skillLabClient.fetchUserProfiles(
                pageable = pageable,
                updatedFrom = currentSync.latestSync.toString()
            )
        }
}
