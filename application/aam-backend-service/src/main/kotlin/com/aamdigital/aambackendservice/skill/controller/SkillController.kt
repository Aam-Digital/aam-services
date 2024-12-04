package com.aamdigital.aambackendservice.skill.controller

import com.aamdigital.aambackendservice.domain.UseCaseOutcome
import com.aamdigital.aambackendservice.error.HttpErrorDto
import com.aamdigital.aambackendservice.skill.core.SearchUserProfileRequest
import com.aamdigital.aambackendservice.skill.core.SearchUserProfileUseCase
import com.aamdigital.aambackendservice.skill.domain.EscoSkill
import com.aamdigital.aambackendservice.skill.domain.SkillUsage
import com.aamdigital.aambackendservice.skill.domain.UserProfile
import com.aamdigital.aambackendservice.skill.repository.SkillLabUserProfileRepository
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

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
) {

    @GetMapping("/user-profile")
    @PreAuthorize("hasAnyAuthority('ROLE_aam_skill_reader')")
    fun fetchUserProfiles(
        fullName: String = "",
        email: String = "",
        phone: String = "",
    ): ResponseEntity<Any> {
        val result = searchUserProfileUseCase.run(
            request = SearchUserProfileRequest(
                fullName = fullName,
                email = email,
                phone = phone,
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

            is UseCaseOutcome.Success<*> -> ResponseEntity.ok().body(result.data)
        }
    }

    @GetMapping("/user-profile/{id}")
    @PreAuthorize("hasAuthority('ROLE_aam_skill_reader')")
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
                            usage = SkillUsage.valueOf(it.usage.uppercase()),
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
}
