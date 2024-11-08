package com.aamdigital.aambackendservice.skill.skilllab

import com.aamdigital.aambackendservice.domain.UseCaseOutcome
import com.aamdigital.aambackendservice.error.AamErrorCode
import com.aamdigital.aambackendservice.skill.core.SyncUserProfileData
import com.aamdigital.aambackendservice.skill.core.SyncUserProfileRequest
import com.aamdigital.aambackendservice.skill.core.SyncUserProfileUseCase
import com.aamdigital.aambackendservice.skill.domain.EscoSkill
import com.aamdigital.aambackendservice.skill.domain.SkillUsage
import com.aamdigital.aambackendservice.skill.domain.UserProfile
import com.aamdigital.aambackendservice.skill.repository.SkillLabUserProfileEntity
import com.aamdigital.aambackendservice.skill.repository.SkillLabUserProfileRepository
import com.aamdigital.aambackendservice.skill.repository.SkillReferenceEntity
import com.fasterxml.jackson.databind.ObjectMapper

enum class SkillLabSyncUserProfileErrorCode : AamErrorCode {
    IO_ERROR
}

/**
 * Will load a specific UserProfile from SkillLab, store it to the database and returns the profile afterwards
 */
class SkillLabSyncUserProfileUseCase(
    private val skillLabClient: SkillLabClient,
    private val skillLabUserProfileRepository: SkillLabUserProfileRepository,
    private val objectMapper: ObjectMapper,
) : SyncUserProfileUseCase() {

    override fun apply(request: SyncUserProfileRequest): UseCaseOutcome<SyncUserProfileData> {
        val userProfile = skillLabClient.fetchUserProfile(
            externalIdentifier = request.userProfile
        )

        val allSkillsEntities = getSkillEntities(userProfile.profile)
        val userProfileEntity = fetchUserProfileEntity(userProfile.profile, allSkillsEntities)

        userProfileEntity.mobileNumber.let {
            if (!it.isNullOrBlank()) {
                userProfileEntity.mobileNumber = formatMobileNumber(it)
            }
        }

        try {
            skillLabUserProfileRepository.save(
                userProfileEntity
            )
        } catch (ex: Exception) {
            return UseCaseOutcome.Failure(
                errorCode = SkillLabSyncUserProfileErrorCode.IO_ERROR,
                errorMessage = ex.localizedMessage,
                cause = ex
            )
        }

        return UseCaseOutcome.Success(
            data = SyncUserProfileData(
                result = UserProfile(
                    id = userProfileEntity.externalIdentifier,
                    fullName = userProfileEntity.fullName,
                    phone = userProfileEntity.mobileNumber,
                    email = userProfileEntity.email,
                    skills = allSkillsEntities.map { skill ->
                        EscoSkill(
                            usage = objectMapper.convertValue(skill.usage.uppercase(), SkillUsage::class.java),
                            escoUri = skill.escoUri
                        )
                    },
                    updatedAtExternalSystem = userProfileEntity.updatedAt,
                    importedAt = userProfileEntity.importedAt?.toInstant(),
                    latestSyncAt = userProfileEntity.latestSyncAt?.toInstant(),
                )
            )
        )
    }

    private fun formatMobileNumber(mobileNumber: String): String = mobileNumber
        .replace(" ", "")
        .replace("-", "")
        .trim()

    private fun fetchUserProfileEntity(
        userProfile: SkillLabProfileDto,
        allSkillsEntities: Set<SkillReferenceEntity>
    ): SkillLabUserProfileEntity = if (skillLabUserProfileRepository.existsByExternalIdentifier(userProfile.id)) {
        val entity = skillLabUserProfileRepository.findByExternalIdentifier(userProfile.id)
        entity.fullName = userProfile.fullName
        entity.mobileNumber = userProfile.mobileNumber
        entity.email = userProfile.email
        entity.skills = allSkillsEntities
        entity.updatedAt = userProfile.updatedAt

        entity
    } else {
        SkillLabUserProfileEntity(
            externalIdentifier = userProfile.id,
            fullName = userProfile.fullName,
            mobileNumber = userProfile.mobileNumber,
            email = userProfile.email,
            skills = allSkillsEntities.toSet(),
            updatedAt = userProfile.updatedAt,
        )
    }

    private fun getSkillEntities(userProfile: SkillLabProfileDto): Set<SkillReferenceEntity> = userProfile.experiences
        .flatMap { it.experiencesSkills }
        .map {
            SkillReferenceEntity(
                externalIdentifier = it.id.toString(),
                escoUri = it.externalId,
                usage = it.choice
            )
        }
        .toSet()
}
