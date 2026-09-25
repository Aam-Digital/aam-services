package com.aamdigital.aambackendservice.skill.repository

interface SkillLabUserProfileRepository {
    fun existsByExternalIdentifier(externalIdentifier: String): Boolean

    fun findByExternalIdentifier(externalIdentifier: String): SkillLabUserProfileEntity

    fun findAll(): List<SkillLabUserProfileEntity>

    /**
     * Stores the profile and, like the database-generated columns of the former table, sets
     * [SkillLabUserProfileEntity.latestSyncAt] and, for a new profile,
     * [SkillLabUserProfileEntity.importedAt] on the given instance.
     */
    fun save(entity: SkillLabUserProfileEntity)
}
