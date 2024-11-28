package com.aamdigital.aambackendservice.skill.core

import com.aamdigital.aambackendservice.domain.UseCaseOutcome
import com.aamdigital.aambackendservice.skill.domain.EscoSkill
import com.aamdigital.aambackendservice.skill.domain.SkillUsage
import com.aamdigital.aambackendservice.skill.domain.UserProfile
import com.aamdigital.aambackendservice.skill.repository.SkillLabUserProfileEntity
import com.aamdigital.aambackendservice.skill.repository.SkillLabUserProfileRepository
import org.springframework.data.domain.Example
import org.springframework.data.domain.ExampleMatcher
import org.springframework.data.domain.Pageable

class SqlSearchUserProfileUseCase(
    private val userProfileRepository: SkillLabUserProfileRepository,
) : SearchUserProfileUseCase() {
    override fun apply(request: SearchUserProfileRequest): UseCaseOutcome<SearchUserProfileData> {

        val matcher = ExampleMatcher.matchingAll()
            .withIgnorePaths(
                "id",
                "externalIdentifier",
                "skills",
                "updatedAt",
                "latestSyncAt",
                "importedAt",
            )
            .withIgnoreCase()
            .withIncludeNullValues()
            .withStringMatcher(ExampleMatcher.StringMatcher.CONTAINING)

        val userProfile = SkillLabUserProfileEntity(
            id = 0,
            externalIdentifier = "",
            fullName = request.fullName,
            mobileNumber = request.phone,
            email = request.email,
            skills = emptySet(),
            updatedAt = "",
            latestSyncAt = null,
            importedAt = null,
        )

        val example = Example.of(userProfile, matcher)

        val searchResults = userProfileRepository.findAll(example, Pageable.ofSize(10))

        if (searchResults.isEmpty) {
            return UseCaseOutcome.Success(
                data = SearchUserProfileData(
                    result = emptyList(),
                )
            )
        }

        return UseCaseOutcome.Success(
            data = SearchUserProfileData(
                result = searchResults.toList().map {
                    UserProfile(
                        id = it.externalIdentifier,
                        fullName = it.fullName,
                        email = it.email,
                        phone = it.mobileNumber,
                        skills = it.skills.map { skill ->
                            EscoSkill(
                                usage = SkillUsage.valueOf(skill.usage.uppercase()),
                                escoUri = skill.externalIdentifier
                            )
                        },
                        latestSyncAt = it.latestSyncAt?.toInstant(),
                        importedAt = it.importedAt?.toInstant(),
                        updatedAtExternalSystem = it.updatedAt
                    )
                }
            )
        )
    }
}
