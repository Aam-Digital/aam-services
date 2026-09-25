package com.aamdigital.aambackendservice.skill.repository

import com.aamdigital.aambackendservice.common.couchdb.core.CouchDbClient
import com.aamdigital.aambackendservice.common.couchdb.core.fetchAllDocumentsByPrefix
import com.aamdigital.aambackendservice.common.error.NotFoundException
import com.aamdigital.aambackendservice.skill.repository.CouchDbSkillLabUserProfileRepository.Companion.SKILL_USER_PROFILE_DATABASE
import com.fasterxml.jackson.databind.ObjectMapper
import java.util.*

/**
 * [SkillLabUserProfileSyncRepository] backed by one CouchDB document per project.
 *
 * Stored next to the profiles rather than with the other backend state: dropping the profile
 * database then also drops the sync state, so the next run is a full sync instead of a delta
 * sync into an empty mirror.
 */
class CouchDbSkillLabUserProfileSyncRepository(
    private val couchDbClient: CouchDbClient,
    private val objectMapper: ObjectMapper
) : SkillLabUserProfileSyncRepository {
    companion object {
        const val DOCUMENT_PREFIX = "SkillLabUserProfileSync"
    }

    override fun findByProjectId(projectId: String): Optional<SkillLabUserProfileSyncEntity> =
        try {
            Optional.of(
                couchDbClient.getDatabaseDocument(
                    database = SKILL_USER_PROFILE_DATABASE,
                    documentId = documentId(projectId),
                    kClass = SkillLabUserProfileSyncEntity::class
                )
            )
        } catch (_: NotFoundException) {
            Optional.empty()
        }

    override fun findAll(): List<SkillLabUserProfileSyncEntity> =
        fetchAllDocumentsByPrefix(
            couchDbClient = couchDbClient,
            objectMapper = objectMapper,
            database = SKILL_USER_PROFILE_DATABASE,
            prefix = DOCUMENT_PREFIX,
            kClass = SkillLabUserProfileSyncEntity::class
        )

    override fun save(entity: SkillLabUserProfileSyncEntity) {
        couchDbClient.putDatabaseDocument(
            database = SKILL_USER_PROFILE_DATABASE,
            documentId = documentId(entity.projectId),
            body = entity
        )
    }

    override fun delete(entity: SkillLabUserProfileSyncEntity) {
        couchDbClient.deleteDatabaseDocument(
            database = SKILL_USER_PROFILE_DATABASE,
            documentId = documentId(entity.projectId)
        )
    }

    private fun documentId(projectId: String) = skillDocumentId(DOCUMENT_PREFIX, projectId)
}
