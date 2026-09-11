package com.aamdigital.aambackendservice.skill.skilllab

import com.aamdigital.aambackendservice.common.domain.UseCaseOutcome
import com.aamdigital.aambackendservice.common.error.AamErrorCode
import com.aamdigital.aambackendservice.skill.core.SyncUserProfileData
import com.aamdigital.aambackendservice.skill.core.SyncUserProfileRequest
import com.aamdigital.aambackendservice.skill.core.SyncUserProfileUseCase
import com.aamdigital.aambackendservice.skill.domain.EscoSkill
import com.aamdigital.aambackendservice.skill.domain.SkillUsage
import com.aamdigital.aambackendservice.skill.domain.UserProfile
import com.aamdigital.aambackendservice.skill.repository.SkillReference
import com.aamdigital.aambackendservice.skill.repository.SkillUserProfile
import com.aamdigital.aambackendservice.skill.repository.SkillUserProfileRepository
import com.fasterxml.jackson.databind.ObjectMapper
import java.time.Instant

enum class SkillLabSyncUserProfileErrorCode : AamErrorCode {
    IO_ERROR
}

/**
 * Will load a specific UserProfile from SkillLab, store it to the database and returns the profile afterwards
 */
class SkillLabSyncUserProfileUseCase(
    private val skillLabClient: SkillLabClient,
    private val skillUserProfileRepository: SkillUserProfileRepository,
    private val objectMapper: ObjectMapper
) : SyncUserProfileUseCase() {
    override fun apply(request: SyncUserProfileRequest): UseCaseOutcome<SyncUserProfileData> {
        val userProfile =
            skillLabClient.fetchUserProfile(
                externalIdentifier = request.userProfile
            )

        val allSkills = getSkills(userProfile.profile)
        val profile = mergeWithStoredProfile(userProfile.profile, allSkills)

        try {
            skillUserProfileRepository.save(profile)
        } catch (ex: Exception) {
            return UseCaseOutcome.Failure(
                errorCode = SkillLabSyncUserProfileErrorCode.IO_ERROR,
                errorMessage = ex.localizedMessage,
                cause = ex
            )
        }

        return UseCaseOutcome.Success(
            data =
                SyncUserProfileData(
                    result =
                        UserProfile(
                            id = profile.externalIdentifier,
                            fullName = profile.fullName,
                            phone = profile.mobileNumber,
                            email = profile.email,
                            skills =
                                allSkills.map { skill ->
                                    EscoSkill(
                                        usage =
                                            objectMapper.convertValue(
                                                skill.usage.uppercase(),
                                                SkillUsage::class.java
                                            ),
                                        escoUri = skill.escoUri
                                    )
                                },
                            updatedAtExternalSystem = profile.updatedAt,
                            importedAt = profile.importedAt,
                            latestSyncAt = profile.latestSyncAt
                        )
                )
        )
    }

    private fun formatMobileNumber(mobileNumber: String): String =
        mobileNumber
            .replace(" ", "")
            .replace("-", "")
            .trim()

    private fun mergeWithStoredProfile(
        userProfile: SkillLabProfileDto,
        allSkills: List<SkillReference>
    ): SkillUserProfile {
        val now = Instant.now()
        val stored = skillUserProfileRepository.findByExternalIdentifier(userProfile.id)

        return SkillUserProfile(
            externalIdentifier = userProfile.id,
            fullName = userProfile.fullName,
            // a blank number is stored as-is, matching the previous behaviour
            mobileNumber = userProfile.mobileNumber?.let { if (it.isBlank()) it else formatMobileNumber(it) },
            email = userProfile.email,
            skills = allSkills,
            updatedAt = userProfile.updatedAt,
            latestSyncAt = now,
            importedAt = stored?.importedAt ?: now
        )
    }

    private fun getSkills(userProfile: SkillLabProfileDto): List<SkillReference> =
        userProfile.experiences
            .flatMap { it.experiencesSkills }
            .map {
                SkillReference(
                    externalIdentifier = it.id.toString(),
                    escoUri = it.externalId,
                    usage = it.choice
                )
            }.distinct()
}
