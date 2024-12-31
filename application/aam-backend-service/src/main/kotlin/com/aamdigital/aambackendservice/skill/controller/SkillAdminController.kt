package com.aamdigital.aambackendservice.skill.controller

import com.aamdigital.aambackendservice.error.HttpErrorDto
import com.aamdigital.aambackendservice.skill.core.FetchUserProfileUpdatesRequest
import com.aamdigital.aambackendservice.skill.core.FetchUserProfileUpdatesUseCase
import com.aamdigital.aambackendservice.skill.repository.SkillLabUserProfileSyncRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.time.ZoneOffset
import kotlin.jvm.optionals.getOrElse

data class SkillDto(
    val projectId: String,
    val latestSync: String,
)

enum class SyncModeDto {
    DELTA,
    FULL,
}

@RestController
@RequestMapping("/v1/skill")
@ConditionalOnProperty(
    prefix = "features.skill-api",
    name = ["mode"],
    havingValue = "skilllab",
    matchIfMissing = false
)
class SkillAdminController(
    private val skillLabFetchUserProfileUpdatesUseCase: FetchUserProfileUpdatesUseCase,
    private val skillLabUserProfileSyncRepository: SkillLabUserProfileSyncRepository,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @GetMapping("/sync")
    @PreAuthorize("hasAuthority('ROLE_skill_admin')")
    fun fetchSyncStatus(): ResponseEntity<List<SkillDto>> {
        val result = skillLabUserProfileSyncRepository.findAll().mapNotNull {
            SkillDto(
                projectId = it.projectId,
                latestSync = it.latestSync.toString()
            )
        }

        return ResponseEntity.ok().body(result)
    }

    /**
     * Fetch data from external system and sync data.
     * For details of parameters like syncMode, see docs/api-specs/skill-api-v1.yaml
     */
    @PostMapping("/sync/{projectId}")
    @PreAuthorize("hasAuthority('ROLE_skill_admin')")
    fun triggerSync(
        @PathVariable projectId: String,
        syncMode: SyncModeDto = SyncModeDto.DELTA,
        updatedFrom: String? = null,
    ): ResponseEntity<Any> {

        val result = skillLabUserProfileSyncRepository.findByProjectId(projectId).getOrElse {
            return ResponseEntity.notFound().build()
        }

        when (syncMode) {
            SyncModeDto.DELTA -> if (!updatedFrom.isNullOrBlank()) {
                result.latestSync = Instant.parse(updatedFrom).atOffset(ZoneOffset.UTC)
                skillLabUserProfileSyncRepository.save(result)
            }

            SyncModeDto.FULL -> skillLabUserProfileSyncRepository.delete(result)
        }

        try {
            skillLabFetchUserProfileUpdatesUseCase.run(
                request = FetchUserProfileUpdatesRequest(
                    projectId = projectId
                )
            )
        } catch (ex: Exception) {
            logger.error(
                "[${this.javaClass.name}] An error occurred: {}",
                ex.localizedMessage,
                ex
            )
            return ResponseEntity.internalServerError().body(
                HttpErrorDto(
                    errorCode = "INTERNAL_SERVER_ERROR",
                    errorMessage = ex.localizedMessage,
                )
            )
        }


        return ResponseEntity.noContent().build()
    }
}
