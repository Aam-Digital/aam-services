package com.aamdigital.aambackendservice.skill.repository

import com.fasterxml.jackson.annotation.JsonFormat
import java.time.OffsetDateTime

data class SkillLabUserProfileEntity(
    var externalIdentifier: String,
    var fullName: String?,
    var mobileNumber: String?,
    var email: String?,
    var skills: Set<SkillReferenceEntity>,
    /**
     * represents the latest update at skillLab
     */
    var updatedAt: String?,
    /**
     * latest update within our system, set by [SkillLabUserProfileRepository.save]
     *
     * The shared ObjectMapper leaves WRITE_DATES_AS_TIMESTAMPS enabled, so without the annotation
     * the timestamp is stored as a numeric epoch value instead of a readable ISO-8601 string.
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    var latestSyncAt: OffsetDateTime? = null,
    /** original date (first latestSyncAt) when added to our system, set by [SkillLabUserProfileRepository.save] */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    var importedAt: OffsetDateTime? = null
)
