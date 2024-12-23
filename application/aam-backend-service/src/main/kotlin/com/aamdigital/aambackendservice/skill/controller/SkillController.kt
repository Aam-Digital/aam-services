package com.aamdigital.aambackendservice.skill.controller

import com.aamdigital.aambackendservice.domain.UseCaseOutcome
import com.aamdigital.aambackendservice.error.HttpErrorDto
import com.aamdigital.aambackendservice.skill.core.SearchUserProfileData
import com.aamdigital.aambackendservice.skill.core.SearchUserProfileRequest
import com.aamdigital.aambackendservice.skill.core.SearchUserProfileUseCase
import com.aamdigital.aambackendservice.skill.domain.EscoSkill
import com.aamdigital.aambackendservice.skill.domain.SkillUsage
import com.aamdigital.aambackendservice.skill.domain.UserProfile
import com.aamdigital.aambackendservice.skill.repository.SkillLabUserProfileRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class PaginationDto(
    val currentPage: Int,
    val pageSize: Int,
    val totalPages: Int,
    val totalElements: Int,
)

data class FetchUserProfilesDto(
    val pagination: PaginationDto,
    val results: List<UserProfile>,
)

@RestController
@RequestMapping("/v1/skill")
@ConditionalOnProperty(
    prefix = "features.skill-api",
    name = ["mode"],
    havingValue = "skilllab",
    matchIfMissing = false
)
class SkillController(
    private val searchUserProfileUseCase: SearchUserProfileUseCase,
    private val userProfileRepository: SkillLabUserProfileRepository,
    private val objectMapper: ObjectMapper,
) {

    companion object {
        private const val MAX_PAGE_SIZE = 100
    }

    @GetMapping("/user-profile")
    @PreAuthorize("hasAnyAuthority('ROLE_skill_reader')")
    fun fetchUserProfiles(
        fullName: String = "",
        email: String = "",
        phone: String = "",
        page: Int = 1,
        pageSize: Int = 10,
    ): ResponseEntity<Any> {
        if (page < 1) {
            return getBadRequestResponse(message = "Page must be greater than 0")
        }

        if (pageSize < 1) {
            return getBadRequestResponse(message = "Page size must not be less than one")
        }

        if (pageSize > MAX_PAGE_SIZE) {
            return getBadRequestResponse(message = "Max pageSize limit is $MAX_PAGE_SIZE")
        }

        val result = searchUserProfileUseCase.run(
            request = SearchUserProfileRequest(
                fullName = fullName,
                email = email,
                phone = phone,
                page = page,
                pageSize = pageSize
            ),
        )

        return when (result) {
            is UseCaseOutcome.Failure<*> -> {
                when (result.errorCode) {
                    else -> ResponseEntity.badRequest().body(
                        ResponseEntity.internalServerError().body(
                            HttpErrorDto(
                                errorCode = result.errorCode.toString(),
                                errorMessage = result.errorMessage
                            )
                        )
                    )
                }
                ResponseEntity.badRequest().body(
                    result.errorMessage
                )
            }

            is UseCaseOutcome.Success<SearchUserProfileData> -> ResponseEntity.ok().body(
                FetchUserProfilesDto(
                    pagination = PaginationDto(
                        currentPage = page,
                        pageSize = pageSize,
                        totalElements = result.data.totalElements,
                        totalPages = result.data.totalPages,
                    ),
                    results = result.data.result
                )
            )
        }
    }

    @GetMapping("/user-profile/{id}")
    @PreAuthorize("hasAuthority('ROLE_skill_reader')")
    fun fetchUserProfile(
        @PathVariable id: String,
    ): ResponseEntity<Any> {
        return if (!userProfileRepository.existsByExternalIdentifier(id)) {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                HttpErrorDto(
                    errorCode = "NOT_FOUND",
                    errorMessage = "No UserProfile found with this id: $id"
                )
            )
        } else {
            val entity = userProfileRepository.findByExternalIdentifier(id)
            ResponseEntity.ok().body(
                UserProfile(
                    id = entity.externalIdentifier,
                    fullName = entity.fullName,
                    phone = entity.mobileNumber,
                    email = entity.email,
                    skills = entity.skills.map {
                        EscoSkill(
                            usage = objectMapper.convertValue(it.usage.uppercase(), SkillUsage::class.java),
                            escoUri = it.escoUri
                        )
                    },
                    updatedAtExternalSystem = entity.updatedAt,
                    importedAt = entity.importedAt?.toInstant(),
                    latestSyncAt = entity.latestSyncAt?.toInstant(),
                )
            )
        }
    }

    private fun getBadRequestResponse(message: String): ResponseEntity<Any> {
        return ResponseEntity.badRequest().body(
            HttpErrorDto(
                errorCode = "BAD_REQUEST",
                errorMessage = message
            )
        )
    }
}
