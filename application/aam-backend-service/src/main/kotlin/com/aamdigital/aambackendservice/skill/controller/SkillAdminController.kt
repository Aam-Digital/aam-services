package com.aamdigital.aambackendservice.skill.controller

import com.aamdigital.aambackendservice.common.error.HttpErrorDto
import com.aamdigital.aambackendservice.skill.ConditionalOnSkillApiEnabled
import com.aamdigital.aambackendservice.skill.ConditionalOnSkillLabMode
import com.aamdigital.aambackendservice.skill.core.FetchUserProfileUpdatesRequest
import com.aamdigital.aambackendservice.skill.core.FetchUserProfileUpdatesUseCase
import com.aamdigital.aambackendservice.skill.di.SkillLabApiClientConfiguration
import com.aamdigital.aambackendservice.skill.repository.SkillUserProfileRepository
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

data class SkillDto(
    val projectId: String,
    val latestSync: String
)

enum class SyncModeDto {
    DELTA,
    FULL
}

@RestController
@RequestMapping("/v1/skill")
@ConditionalOnSkillApiEnabled
@ConditionalOnSkillLabMode
class SkillAdminController(
    private val skillLabFetchUserProfileUpdatesUseCase: FetchUserProfileUpdatesUseCase,
    private val skillUserProfileRepository: SkillUserProfileRepository,
    private val skillLabApiClientConfiguration: SkillLabApiClientConfiguration
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * The sync state is derived from the stored profiles rather than read from a cursor table:
     * the newest `latestSyncAt` *is* the point up to which this project is synced.
     */
    @GetMapping("/sync")
    @PreAuthorize("hasAuthority('ROLE_skill_admin')")
    fun fetchSyncStatus(): ResponseEntity<List<SkillDto>> {
        val latestSync = latestSync() ?: return ResponseEntity.ok().body(emptyList())

        return ResponseEntity.ok().body(
            listOf(
                SkillDto(
                    projectId = skillLabApiClientConfiguration.projectId,
                    latestSync = latestSync.toString()
                )
            )
        )
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
        updatedFrom: String? = null
    ): ResponseEntity<Any> {
        try {
            skillLabFetchUserProfileUpdatesUseCase.run(
                request =
                    FetchUserProfileUpdatesRequest(
                        projectId = projectId,
                        // an explicit updatedFrom overrides the derived cursor for this run only
                        updatedFrom = updatedFrom?.takeUnless { it.isBlank() }?.let { Instant.parse(it) },
                        fullSync = syncMode == SyncModeDto.FULL
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
                    errorMessage = ex.localizedMessage
                )
            )
        }

        return ResponseEntity.noContent().build()
    }

    private fun latestSync(): Instant? = skillUserProfileRepository.findAll().mapNotNull { it.latestSyncAt }.maxOrNull()
}
