package com.aamdigital.aambackendservice.skill.repository

import java.util.*

interface SkillLabUserProfileSyncRepository {
    fun findByProjectId(projectId: String): Optional<SkillLabUserProfileSyncEntity>

    fun findAll(): List<SkillLabUserProfileSyncEntity>

    fun save(entity: SkillLabUserProfileSyncEntity)

    fun delete(entity: SkillLabUserProfileSyncEntity)
}
