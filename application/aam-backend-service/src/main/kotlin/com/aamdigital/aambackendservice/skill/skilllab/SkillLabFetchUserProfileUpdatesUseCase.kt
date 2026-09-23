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
import java.time.OffsetDateTime

enum class SkillLabFetchUserProfileUpdatesErrorCode : AamErrorCode {
    EXTERNAL_SYSTEM_ERROR,
    EVENT_PUBLISH_ERROR
}

/**
 * Fetch latest changes for this SkillLab tenant and create SyncUserProfileEvents for each changed UserProfile
 *
 * The delta cursor is derived from the stored profiles rather than kept in a table of its own: it
 * is the newest `updatedAt` the external system reported for any stored profile. That timestamp
 * comes from the external system's own clock, the same clock `updated_from` is compared against,
 * so a profile changed there after this run fetched its list normally stays newer than the cursor
 * and is picked up next time. Our own `latestSyncAt` would be a worse cursor: it is taken when the
 * queued update is consumed, possibly minutes later, and would skip everything changed in between.
 * One narrower gap remains: if another profile of the same run is changed again before it is
 * consumed, its newer `updatedAt` can overtake a profile changed in the meantime - a FULL sync
 * repairs that. A run whose events never reached the consumer does not advance the cursor. An
 * empty store means a full sync.
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

    /**
     * The delta cursor, see the class documentation. Falls back to our own `latestSyncAt` only if
     * the external system reported no parsable `updatedAt` at all.
     */
    private fun latestSync(): Instant? {
        val profiles = skillUserProfileRepository.findAll()

        return profiles.mapNotNull { parseExternalTimestamp(it.updatedAt) }.maxOrNull()
            ?: profiles.mapNotNull { it.latestSyncAt }.maxOrNull()
    }

    private fun parseExternalTimestamp(value: String?): Instant? =
        value?.takeIf { it.isNotBlank() }?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() }
}
