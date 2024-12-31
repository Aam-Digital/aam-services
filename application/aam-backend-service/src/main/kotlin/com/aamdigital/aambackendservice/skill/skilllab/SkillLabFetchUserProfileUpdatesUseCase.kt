package com.aamdigital.aambackendservice.skill.skilllab

import com.aamdigital.aambackendservice.domain.DomainReference
import com.aamdigital.aambackendservice.domain.UseCaseOutcome
import com.aamdigital.aambackendservice.error.AamErrorCode
import com.aamdigital.aambackendservice.error.AamException
import com.aamdigital.aambackendservice.skill.core.FetchUserProfileUpdatesData
import com.aamdigital.aambackendservice.skill.core.FetchUserProfileUpdatesRequest
import com.aamdigital.aambackendservice.skill.core.FetchUserProfileUpdatesUseCase
import com.aamdigital.aambackendservice.skill.core.UserProfileUpdatePublisher
import com.aamdigital.aambackendservice.skill.core.event.UserProfileUpdateEvent
import com.aamdigital.aambackendservice.skill.di.UserProfileUpdateEventQueueConfiguration
import com.aamdigital.aambackendservice.skill.repository.SkillLabUserProfileSyncEntity
import com.aamdigital.aambackendservice.skill.repository.SkillLabUserProfileSyncRepository
import org.springframework.data.domain.Pageable
import java.time.Instant
import java.time.ZoneOffset
import kotlin.jvm.optionals.getOrNull

enum class SkillLabFetchUserProfileUpdatesErrorCode : AamErrorCode {
    EXTERNAL_SYSTEM_ERROR, EVENT_PUBLISH_ERROR
}

/**
 * Fetch latest changes for this SkillLab tenant and create SyncUserProfileEvents for each changed UserProfile
 */
class SkillLabFetchUserProfileUpdatesUseCase(
    private val skillLabClient: SkillLabClient,
    private val skillLabUserProfileSyncRepository: SkillLabUserProfileSyncRepository,
    private val userProfileUpdatePublisher: UserProfileUpdatePublisher,
) : FetchUserProfileUpdatesUseCase() {

    companion object {
        private const val PAGE_SIZE = 50
        private const val MAX_RESULTS_LIMIT = 10_000
    }

    override fun apply(request: FetchUserProfileUpdatesRequest): UseCaseOutcome<FetchUserProfileUpdatesData> {
        val results = mutableListOf<DomainReference>()
        var currentSync = skillLabUserProfileSyncRepository.findByProjectId(request.projectId).getOrNull()
        var page = 1

        do {
            val batch = try {
                fetchNextBatch(
                    pageable = Pageable.ofSize(PAGE_SIZE).withPage(page++),
                    currentSync = currentSync,
                )
            } catch (ex: AamException) {
                return UseCaseOutcome.Failure(
                    errorCode = SkillLabFetchUserProfileUpdatesErrorCode.EXTERNAL_SYSTEM_ERROR,
                    errorMessage = ex.localizedMessage,
                    cause = ex,
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
                    cause = ex,
                )
            }
        }

        if (currentSync != null) {
            currentSync.latestSync = Instant.now().atOffset(ZoneOffset.UTC)
        } else {
            currentSync = SkillLabUserProfileSyncEntity(
                projectId = request.projectId,
                latestSync = Instant.now().atOffset(ZoneOffset.UTC),
            )
        }

        skillLabUserProfileSyncRepository.save(currentSync)

        return UseCaseOutcome.Success(
            data = FetchUserProfileUpdatesData(
                result = results.map {
                    DomainReference(id = it.id)
                }
            )
        )
    }

    private fun fetchNextBatch(
        pageable: Pageable,
        currentSync: SkillLabUserProfileSyncEntity?,
    ): List<DomainReference> = if (currentSync == null) {
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
