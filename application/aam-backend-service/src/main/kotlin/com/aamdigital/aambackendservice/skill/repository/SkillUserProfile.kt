package com.aamdigital.aambackendservice.skill.repository

import com.fasterxml.jackson.annotation.JsonFormat
import java.time.Instant

/**
 * A skill reference as reported by the external system.
 */
data class SkillReference(
    val externalIdentifier: String,
    val escoUri: String,
    val usage: String
)

/**
 * Local mirror of one profile in the external skill system.
 *
 * This is not intermediate data: the module's product is the search API over these profiles, which
 * the frontend queries to link an external profile to a case. It is rebuildable, though - a full
 * re-sync restores it from the external system.
 */
data class SkillUserProfile(
    val externalIdentifier: String,
    val fullName: String?,
    val mobileNumber: String?,
    val email: String?,
    val skills: List<SkillReference> = emptyList(),
    /** latest update at the external system, as reported by it */
    val updatedAt: String?,
    /**
     * Latest update within our system. Doubles as the delta-sync cursor: the next fetch asks the
     * external system for everything changed since the newest of these, so no separate cursor has
     * to be stored.
     *
     * The shared ObjectMapper leaves WRITE_DATES_AS_TIMESTAMPS enabled, so without the annotation
     * an Instant is stored as a numeric epoch value instead of a readable ISO-8601 string.
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    val latestSyncAt: Instant? = null,
    /** first time this profile was seen by our system */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    val importedAt: Instant? = null
)

interface SkillUserProfileRepository {
    fun findByExternalIdentifier(externalIdentifier: String): SkillUserProfile?

    fun findAll(): List<SkillUserProfile>

    fun save(profile: SkillUserProfile)
}
