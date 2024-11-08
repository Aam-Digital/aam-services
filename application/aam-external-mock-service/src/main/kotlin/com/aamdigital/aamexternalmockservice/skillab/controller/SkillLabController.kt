package com.aamdigital.aamexternalmockservice.skillab.controller

import com.aamdigital.aamexternalmockservice.skillab.error.SkillLabError
import com.aamdigital.aamexternalmockservice.skillab.error.SkillLabErrorResponseDto
import com.aamdigital.aamexternalmockservice.skillab.repository.ProfileCrudRepository
import com.aamdigital.aamexternalmockservice.skillab.repository.ProfileEntity
import com.aamdigital.aamexternalmockservice.skillab.repository.ProfilePagingRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.*

// SkillLab API
data class UserProfileResponse(
    val pagination: PaginationDto,
    val results: List<ProfileEntity>,
)

// SkillLab API
data class PaginationDto(
    val currentPage: Int,
    val perPage: Int,
    val totalEntries: Int,
)

@RestController
@RequestMapping("/skilllab")
class SkillLabController(
    val objectMapper: ObjectMapper,
    val profilePagingRepository: ProfilePagingRepository,
    val profileCrudRepository: ProfileCrudRepository,
) {

    // SkillLab API
    @GetMapping("/profiles")
    fun getProfiles(
        currentPage: Int = 0,
        perPage: Int = 100,
    ): ResponseEntity<UserProfileResponse> {
        val pageable = Pageable.ofSize(perPage).withPage(currentPage)
        return UserProfileResponse(
            pagination = PaginationDto(
                currentPage = pageable.pageNumber,
                perPage = pageable.pageSize,
                totalEntries = profileCrudRepository.count().toInt(),
            ),
            results = profilePagingRepository.findAll(pageable).toList(),
        ).let { ResponseEntity.ok(it) }
    }

    // SkillLab API
    @GetMapping("/profiles/{profileId}")
    fun getProfile(
        @PathVariable profileId: UUID,
    ): ResponseEntity<Any> {
        return profileCrudRepository.findById(profileId).let { ResponseEntity.ok(it) }
    }

    @RequestMapping("/**")
    fun handleInvalidPaths(): ResponseEntity<Any> {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            objectMapper.writeValueAsString(
                SkillLabErrorResponseDto(
                    error = SkillLabError.NotFound()
                )
            )
        )
    }
}
