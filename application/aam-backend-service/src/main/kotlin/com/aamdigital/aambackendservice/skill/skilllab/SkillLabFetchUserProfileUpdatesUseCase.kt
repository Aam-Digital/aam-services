package com.aamdigital.aambackendservice.skill.skilllab

import com.aamdigital.aambackendservice.common.domain.DomainReference
import com.aamdigital.aambackendservice.common.domain.UseCaseOutcome
import com.aamdigital.aambackendservice.common.error.AamErrorCode
import com.aamdigital.aambackendservice.common.error.AamException
import com.aamdigital.aambackendservice.skill.core.FetchUserProfileUpdatesData
import com.aamdigital.aambackendservice.skill.core.FetchUserProfileUpdatesRequest
import com.aamdigital.aambackendservice.skill.core.FetchUserProfileUpdatesUseCase
import com.aamdigital.aambackendservice.skill.core.UserProfileUpdatePublisher
import com.aamdigital.aambackendservice.skill.core.event.UserProfileUpdateEvent
import com.aamdigital.aambackendservice.skill.di.UserProfileUpdateEventQueueConfiguration
import com.aamdigital.aambackendservice.skill.repository.SkillUserProfileRepository
import java.time.Instant

enum class SkillLabFetchUserProfileUpdatesErrorCode : AamErrorCode {
    EXTERNAL_SYSTEM_ERROR,
    EVENT_PUBLISH_ERROR
}

/**
 * Fetch latest changes for this SkillLab tenant and create SyncUserProfileEvents for each changed UserProfile
 *
 * The delta cursor is derived from the stored profiles rather than kept in a table of its own: the
 * newest `latestSyncAt` is by definition the point up to which profiles were actually persisted.
 * That also makes it self-healing - a run whose events never reached the consumer does not advance
 * the cursor, so the next run picks those profiles up again. An empty store means a full sync.
 */
class SkillLabFetchUserProfileUpdatesUseCase(
    private val skillLabClient: SkillLabClient,
    private val skillUserProfileRepository: SkillUserProfileRepository,
    private val userProfileUpdatePublisher: UserProfileUpdatePublisher
) : FetchUserProfileUpdatesUseCase() {
    companion object {
        private const val PAGE_SIZE = 50
        private const val MAX_RESULTS_LIMIT = 10_000
    }

    override fun apply(request: FetchUserProfileUpdatesRequest): UseCaseOutcome<FetchUserProfileUpdatesData> {
        val results = mutableListOf<DomainReference>()
        val updatedFrom = if (request.fullSync) null else request.updatedFrom ?: latestSync()
        var page = 1

        do {
            val batch =
                try {
                    skillLabClient.fetchUserProfiles(
                        page = page++,
                        pageSize = PAGE_SIZE,
                        updatedFrom = updatedFrom?.toString()
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

        results.forEach {
            try {
                userProfileUpdatePublisher.publish(
                    UserProfileUpdateEventQueueConfiguration.USER_PROFILE_UPDATE_QUEUE,
                    UserProfileUpdateEvent(
                        projectId = request.projectId,
                        userProfileId = it.id
                    )
                )
            } catch (ex: Exception) {
                return UseCaseOutcome.Failure(
                    errorCode = SkillLabFetchUserProfileUpdatesErrorCode.EVENT_PUBLISH_ERROR,
                    errorMessage = ex.localizedMessage,
                    cause = ex
                )
            }
        }

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

    /** The delta cursor: the most recent point at which a profile was successfully stored. */
    private fun latestSync(): Instant? = skillUserProfileRepository.findAll().mapNotNull { it.latestSyncAt }.maxOrNull()
}
