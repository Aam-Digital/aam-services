package com.aamdigital.aambackendservice.skill.core

import com.aamdigital.aambackendservice.common.domain.UseCaseOutcome
import com.aamdigital.aambackendservice.skill.domain.EscoSkill
import com.aamdigital.aambackendservice.skill.domain.SkillUsage
import com.aamdigital.aambackendservice.skill.domain.UserProfile
import com.aamdigital.aambackendservice.skill.repository.SkillUserProfile
import com.aamdigital.aambackendservice.skill.repository.SkillUserProfileRepository
import com.fasterxml.jackson.databind.ObjectMapper

/**
 * Search for a stored profile with the information from [SearchUserProfileRequest].
 *
 * 1. exact email matches
 * 2. mobile phone matches
 * 3. name matches
 * 4. failing that, matches on the first and last word of the name separately
 *
 * The first stage that yields anything wins; stages are not merged.
 *
 * Matching runs in the JVM over the whole mirror rather than in the store. The profile set is
 * small, the module has no client today, and this keeps the store a plain document store. If the
 * module is ever adopted at scale, caching the profile list is the first thing to add.
 */
class InMemorySearchUserProfileUseCase(
    private val userProfileRepository: SkillUserProfileRepository,
    private val objectMapper: ObjectMapper
) : SearchUserProfileUseCase() {
    override fun apply(request: SearchUserProfileRequest): UseCaseOutcome<SearchUserProfileData> {
        val profiles = userProfileRepository.findAll()

        val matches =
            matchByEmail(profiles, request.email)
                .ifEmpty { matchByPhone(profiles, request.phone) }
                .ifEmpty { matchByName(profiles, request.fullName) }
                .ifEmpty { matchByNameParts(profiles, request.fullName) }

        return UseCaseOutcome.Success(data = page(matches, request.page, request.pageSize))
    }

    private fun matchByEmail(
        profiles: List<SkillUserProfile>,
        email: String?
    ): List<SkillUserProfile> {
        if (email.isNullOrBlank()) return emptyList()
        return profiles.filter { it.email.equals(email, ignoreCase = true) }
    }

    private fun matchByPhone(
        profiles: List<SkillUserProfile>,
        phone: String?
    ): List<SkillUserProfile> {
        if (phone.isNullOrBlank()) return emptyList()
        return profiles.filter { it.mobileNumber?.contains(phone, ignoreCase = true) == true }
    }

    private fun matchByName(
        profiles: List<SkillUserProfile>,
        fullName: String?
    ): List<SkillUserProfile> {
        if (fullName.isNullOrBlank()) return emptyList()
        return profiles.filter { it.fullName?.contains(fullName, ignoreCase = true) == true }
    }

    /**
     * "Ada Lovelace" also matches a profile stored as "Ada B. Lovelace": the first and last word
     * are searched separately and the results combined.
     */
    private fun matchByNameParts(
        profiles: List<SkillUserProfile>,
        fullName: String?
    ): List<SkillUserProfile> {
        if (fullName.isNullOrBlank()) return emptyList()

        val nameParts = fullName.split(" ").filter { it.isNotBlank() }
        if (nameParts.size < 2) return emptyList()

        return listOf(nameParts.first(), nameParts.last())
            .flatMap { part -> matchByName(profiles, part) }
            .distinctBy { it.externalIdentifier }
    }

    private fun page(
        matches: List<SkillUserProfile>,
        page: Int,
        pageSize: Int
    ): SearchUserProfileData {
        val ordered = matches.sortedBy { it.externalIdentifier }
        val fromIndex = ((page - 1) * pageSize).coerceAtMost(ordered.size)
        val toIndex = (fromIndex + pageSize).coerceAtMost(ordered.size)

        return SearchUserProfileData(
            result = ordered.subList(fromIndex, toIndex).map { toDto(it) },
            totalElements = ordered.size,
            totalPages = if (ordered.isEmpty()) 0 else (ordered.size + pageSize - 1) / pageSize
        )
    }

    private fun toDto(profile: SkillUserProfile): UserProfile =
        UserProfile(
            id = profile.externalIdentifier,
            fullName = profile.fullName,
            email = profile.email,
            phone = profile.mobileNumber,
            skills =
                profile.skills.map { skill ->
                    EscoSkill(
                        usage = objectMapper.convertValue(skill.usage.uppercase(), SkillUsage::class.java),
                        escoUri = skill.escoUri
                    )
                },
            latestSyncAt = profile.latestSyncAt,
            importedAt = profile.importedAt,
            updatedAtExternalSystem = profile.updatedAt
        )
}
