package com.aamdigital.aambackendservice.skill.controller

import com.aamdigital.aambackendservice.domain.UseCaseOutcome
import com.aamdigital.aambackendservice.error.HttpErrorDto
import com.aamdigital.aambackendservice.skill.core.FetchUserProfileUpdatesRequest
import com.aamdigital.aambackendservice.skill.core.FetchUserProfileUpdatesUseCase
import com.aamdigital.aambackendservice.skill.core.SearchUserProfileRequest
import com.aamdigital.aambackendservice.skill.core.SearchUserProfileUseCase
import com.aamdigital.aambackendservice.skill.domain.EscoSkill
import com.aamdigital.aambackendservice.skill.domain.SkillUsage
import com.aamdigital.aambackendservice.skill.domain.UserProfile
import com.aamdigital.aambackendservice.skill.repository.SkillLabUserProfileRepository
import com.aamdigital.aambackendservice.skill.skilllab.SkillLabFetchUserProfileUpdatesErrorCode
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/skill")
class SkillController(
    private val fetchUserProfileUpdatesUseCase: FetchUserProfileUpdatesUseCase, // todo needs no-op implementation
    private val searchUserProfileUseCase: SearchUserProfileUseCase, // todo needs no-op implementation
    private val userProfileRepository: SkillLabUserProfileRepository
) {

    @GetMapping("/user-profile")
    fun fetchUserProfiles(
        fullName: String?,
        email: String?,
        phone: String?,
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
//                    SkillLabFetchUserProfileUpdatesErrorCode.EXTERNAL_SYSTEM_ERROR
//                        -> ResponseEntity.internalServerError().body(
//                        HttpErrorDto(
//                            errorCode = result.errorCode.toString(),
//                            errorMessage = result.errorMessage
//                        )
//                    )

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
                            escoUri = it.externalIdentifier
                        )
                    },
                    updatedAtExternalSystem = entity.updatedAt,
                    importedAt = entity.importedAt?.toInstant(),
                    latestSyncAt = entity.latestSyncAt?.toInstant(),
                )
            )
        }
    }

    @PostMapping
    fun fetchUserProfileUpdates(
        @RequestBody request: FetchUserProfileUpdatesRequest,
    ): ResponseEntity<Any> {
        val result = fetchUserProfileUpdatesUseCase.run(
            request = request
        )

        return when (result) {
            is UseCaseOutcome.Failure<*> -> {
                when (result.errorCode) {
                    SkillLabFetchUserProfileUpdatesErrorCode.EXTERNAL_SYSTEM_ERROR
                        -> ResponseEntity.internalServerError().body(
                        HttpErrorDto(
                            errorCode = result.errorCode.toString(),
                            errorMessage = result.errorMessage
                        )
                    )

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
}
