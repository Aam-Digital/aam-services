package com.aamdigital.aambackendservice.skill.core

import com.aamdigital.aambackendservice.domain.UseCaseOutcome
import com.aamdigital.aambackendservice.skill.domain.EscoSkill
import com.aamdigital.aambackendservice.skill.domain.SkillUsage
import com.aamdigital.aambackendservice.skill.domain.UserProfile
import com.aamdigital.aambackendservice.skill.repository.SkillLabUserProfileEntity
import com.aamdigital.aambackendservice.skill.repository.SkillLabUserProfileRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.data.domain.Example
import org.springframework.data.domain.ExampleMatcher
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable

/**
 * Search for an SkillLabUserProfile with provided information from SearchUserProfileRequest
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
    private val objectMapper: ObjectMapper,
) : SearchUserProfileUseCase() {

    companion object {
        val MATCHER_EMAIL = ExampleMatcher.matchingAny()
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

        val MATCHER_PHONE = ExampleMatcher.matchingAny()
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

        val MATCHER_NAME = ExampleMatcher.matchingAll()
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

        var searchResults: Page<SkillLabUserProfileEntity>
        val pageable = Pageable.ofSize(request.pageSize).withPage(request.page - 1)

        // check for exact matches for email
        if (!userProfile.email.isNullOrBlank()) {
            searchResults = userProfileRepository.findAll(
                Example.of(userProfile, MATCHER_EMAIL), pageable
            )

            if (!searchResults.isEmpty) {
                return asSuccessResponse(searchResults)
            }
        }

        // check for matches for phone
        if (!userProfile.mobileNumber.isNullOrBlank()) {
            searchResults = userProfileRepository.findAll(
                Example.of(userProfile, MATCHER_PHONE), pageable
            )

            if (!searchResults.isEmpty) {
                return asSuccessResponse(searchResults)
            }
        }

        // check for name
        searchResults = if (!userProfile.fullName.isNullOrBlank()) {
            userProfileRepository.findAll(
                Example.of(userProfile, MATCHER_NAME), pageable
            )
        } else {
            Page.empty()
        }

        // search runs with just firstName/lastName
        if (searchResults.isEmpty && !userProfile.fullName.isNullOrBlank()) {
            val nameParts = userProfile.fullName?.split(" ") ?: listOf()

            if (nameParts.size < 2) {
                return asSuccessResponse(searchResults)
            }

            val searchNames = listOf(nameParts.first(), nameParts.last())
            val partNameSearchResults = mutableSetOf<SkillLabUserProfileEntity>()

            searchNames.forEach { name ->
                userProfile.fullName = name
                partNameSearchResults.addAll(
                    userProfileRepository.findAll(
                        Example.of(userProfile, MATCHER_NAME), pageable
                    ).toList()
                )
            }

            val result = partNameSearchResults.toList().take(request.pageSize)

            searchResults = PageImpl(
                result,
            )
        }

        return asSuccessResponse(searchResults)
    }

    private fun asSuccessResponse(
        results: Page<SkillLabUserProfileEntity>,
    ): UseCaseOutcome.Success<SearchUserProfileData> = UseCaseOutcome.Success(
        data = SearchUserProfileData(
            result = results.toList().map {
                toDto(it)
            },
            totalElements = results.totalElements.toInt(),
            totalPages = results.totalPages
        )
    )

    private fun toDto(it: SkillLabUserProfileEntity): UserProfile {
        return UserProfile(
            id = it.externalIdentifier,
            fullName = it.fullName,
            email = it.email,
            phone = it.mobileNumber,
            skills = it.skills.map { skill ->
                EscoSkill(
                    usage = objectMapper.convertValue(skill.usage.uppercase(), SkillUsage::class.java),
                    escoUri = skill.escoUri
                )
            },
            latestSyncAt = it.latestSyncAt?.toInstant(),
            importedAt = it.importedAt?.toInstant(),
            updatedAtExternalSystem = it.updatedAt
        )
    }
}
