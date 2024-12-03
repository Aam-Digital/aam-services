package com.aamdigital.aambackendservice.skill.core

import com.aamdigital.aambackendservice.domain.UseCaseOutcome
import com.aamdigital.aambackendservice.skill.domain.EscoSkill
import com.aamdigital.aambackendservice.skill.domain.SkillUsage
import com.aamdigital.aambackendservice.skill.domain.UserProfile
import com.aamdigital.aambackendservice.skill.repository.SkillLabUserProfileEntity
import com.aamdigital.aambackendservice.skill.repository.SkillLabUserProfileRepository
import org.springframework.data.domain.Example
import org.springframework.data.domain.ExampleMatcher
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

/**
 * Search for an SkillLabUserProfile with provides information from SearchUserProfileRequest
 *
 * 1. search for exact email matches
 * 2. search for mobile phone matches
 * 3. search for name
 *
 * The first exact match will be returned. Otherwise, a list of possible matches is returned
 *
 */
class SqlSearchUserProfileUseCase(
    private val userProfileRepository: SkillLabUserProfileRepository,
) : SearchUserProfileUseCase() {

    companion object {
        private const val MAX_RESULTS = 10;

        private val MATCHER_EMAIL = ExampleMatcher.matchingAny()
            .withIgnorePaths(
                "id",
                "externalIdentifier",
                "fullName",
                "mobileNumber",
                "skills",
                "updatedAt",
                "latestSyncAt",
                "importedAt",
            )
            .withIgnoreCase()
            .withIncludeNullValues()
            .withStringMatcher(ExampleMatcher.StringMatcher.EXACT)

        private val MATCHER_PHONE = ExampleMatcher.matchingAny()
            .withIgnorePaths(
                "id",
                "externalIdentifier",
                "fullName",
                "email",
                "skills",
                "updatedAt",
                "latestSyncAt",
                "importedAt",
            )
            .withIgnoreCase()
            .withIncludeNullValues()
            .withStringMatcher(ExampleMatcher.StringMatcher.CONTAINING)

        private val MATCHER_NAME = ExampleMatcher.matchingAll()
            .withIgnorePaths(
                "id",
                "externalIdentifier",
                "mobileNumber",
                "email",
                "skills",
                "updatedAt",
                "latestSyncAt",
                "importedAt",
            )
            .withIgnoreCase()
            .withIncludeNullValues()
            .withStringMatcher(ExampleMatcher.StringMatcher.CONTAINING)
    }

    override fun apply(request: SearchUserProfileRequest): UseCaseOutcome<SearchUserProfileData> {

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

        var searchResults: Page<SkillLabUserProfileEntity>;

        // check for exact matches for email
        if (!userProfile.email.isNullOrBlank()) {
            searchResults = userProfileRepository.findAll(
                Example.of(userProfile, MATCHER_EMAIL), Pageable.ofSize(MAX_RESULTS)
            )

            if (!searchResults.isEmpty) {
                return UseCaseOutcome.Success(
                    data = SearchUserProfileData(
                        result = searchResults.toList().map {
                            toDto(it)
                        }
                    )
                )
            }
        }

        // check for matches for phone
        if (!userProfile.mobileNumber.isNullOrBlank()) {
            searchResults = userProfileRepository.findAll(
                Example.of(userProfile, MATCHER_PHONE), Pageable.ofSize(MAX_RESULTS)
            )

            if (!searchResults.isEmpty) {
                return UseCaseOutcome.Success(
                    data = SearchUserProfileData(
                        result = searchResults.toList().map {
                            toDto(it)
                        }
                    )
                )
            }
        }

        // check for name
        searchResults = if (!userProfile.fullName.isNullOrBlank()) {
            userProfileRepository.findAll(
                Example.of(userProfile, MATCHER_NAME), Pageable.ofSize(MAX_RESULTS)
            )
        } else {
            Page.empty()
        }

        return UseCaseOutcome.Success(
            data = SearchUserProfileData(
                result = searchResults.toList().map {
                    toDto(it)
                }
            )
        )
    }

    private fun toDto(it: SkillLabUserProfileEntity): UserProfile {
        return UserProfile(
            id = it.externalIdentifier,
            fullName = it.fullName,
            email = it.email,
            phone = it.mobileNumber,
            skills = it.skills.map { skill ->
                EscoSkill(
                    usage = SkillUsage.valueOf(skill.usage.uppercase()),
                    escoUri = skill.escoUri
                )
            },
            latestSyncAt = it.latestSyncAt?.toInstant(),
            importedAt = it.importedAt?.toInstant(),
            updatedAtExternalSystem = it.updatedAt
        )
    }
}
