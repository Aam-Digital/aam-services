package com.aamdigital.aambackendservice.skill.repository

import org.springframework.data.repository.CrudRepository
import java.util.*

interface SkillLabUserProfileSyncRepository : CrudRepository<SkillLabUserProfileSyncEntity, Long> {

    fun findByProjectId(projectId: String): Optional<SkillLabUserProfileSyncEntity>
}
