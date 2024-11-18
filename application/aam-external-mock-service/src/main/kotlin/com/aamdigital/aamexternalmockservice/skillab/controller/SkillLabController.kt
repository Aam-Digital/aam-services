package com.aamdigital.aamexternalmockservice.skillab.controller

import com.aamdigital.aamexternalmockservice.skillab.error.SkillLabError
import com.aamdigital.aamexternalmockservice.skillab.error.SkillLabErrorResponseDto
import com.aamdigital.aamexternalmockservice.skillab.repository.ProfileCrudRepository
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
data class UserProfilesResponse(
  val pagination: PaginationDto,
  val results: List<ProfileIdDto>,
)

// SkillLab API
data class ProfileIdDto(
  val id: String,
)

// SkillLab API
data class InvalidPage(
  val error: String,
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
    page: Int = 1,
    perPage: Int = 100,
  ): ResponseEntity<Any> {
    if (page < 1) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(InvalidPage(error = "The page you were looking for doesn't exist (404)"))
    }

    val pageable = Pageable.ofSize(perPage).withPage(page - 1)
    return UserProfilesResponse(
      pagination = PaginationDto(
        currentPage = pageable.pageNumber + 1,
        perPage = pageable.pageSize,
        totalEntries = profileCrudRepository.count().toInt(),
      ),
      results = profilePagingRepository.findAll(pageable).map {
        ProfileIdDto(
          id = it.id.toString()
        )
      }.toList(),
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
