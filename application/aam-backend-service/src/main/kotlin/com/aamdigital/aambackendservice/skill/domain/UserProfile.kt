package com.aamdigital.aambackendservice.skill.domain

import java.time.Instant

/**
 * Representation of an Individual in an external system
 */
data class UserProfile(
    var id: String,
    var fullName: String?,
    var phone: String?,
    var email: String?,
    var skills: List<EscoSkill>,
    var updatedAtExternalSystem: String?,
    var importedAt: Instant? = null,
    var latestSyncAt: Instant? = null,
)
