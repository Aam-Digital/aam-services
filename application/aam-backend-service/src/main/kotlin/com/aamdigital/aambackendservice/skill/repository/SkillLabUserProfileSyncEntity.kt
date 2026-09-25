package com.aamdigital.aambackendservice.skill.repository

import com.fasterxml.jackson.annotation.JsonFormat
import java.time.OffsetDateTime

/**
 * Stores the latest successful SkillLabFetchUserProfileUpdateUseCase run date for each project.
 */
data class SkillLabUserProfileSyncEntity(
    var projectId: String,
    /**
     * The shared ObjectMapper leaves WRITE_DATES_AS_TIMESTAMPS enabled, so without this the
     * timestamp is stored as a numeric epoch value instead of a readable ISO-8601 string.
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    var latestSync: OffsetDateTime
)
