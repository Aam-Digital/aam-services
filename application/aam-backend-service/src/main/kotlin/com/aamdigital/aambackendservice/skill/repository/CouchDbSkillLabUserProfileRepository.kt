package com.aamdigital.aambackendservice.skill.repository

import com.aamdigital.aambackendservice.common.couchdb.core.CouchDbClient
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.ZoneOffset

/** The identifier comes from a third party, so it is encoded rather than trusted as a doc id. */
internal fun skillDocumentId(
    prefix: String,
    identifier: String
) = "$prefix:${URLEncoder.encode(identifier, StandardCharsets.UTF_8)}"

/**
 * [SkillLabUserProfileRepository] backed by one CouchDB document per profile, in a database of its
 * own so the mirror - together with its sync state, see [CouchDbSkillLabUserProfileSyncRepository] -
 * can be dropped and rebuilt without touching anything else.
 */
class CouchDbSkillLabUserProfileRepository(
    private val couchDbClient: CouchDbClient
) : SkillLabUserProfileRepository {
    companion object {
        const val SKILL_USER_PROFILE_DATABASE = "skill-user-profile"
        const val DOCUMENT_PREFIX = "SkillProfile"
    }

    override fun existsByExternalIdentifier(externalIdentifier: String): Boolean =
        couchDbClient
            .headDatabaseDocument(
                database = SKILL_USER_PROFILE_DATABASE,
                documentId = documentId(externalIdentifier)
            ).eTag != null

    override fun findByExternalIdentifier(externalIdentifier: String): SkillLabUserProfileEntity =
        couchDbClient.getDatabaseDocument(
            database = SKILL_USER_PROFILE_DATABASE,
            documentId = documentId(externalIdentifier),
            kClass = SkillLabUserProfileEntity::class
        )

    override fun findAll(): List<SkillLabUserProfileEntity> =
        couchDbClient.getDatabaseDocumentsByPrefix(
            database = SKILL_USER_PROFILE_DATABASE,
            prefix = DOCUMENT_PREFIX,
            kClass = SkillLabUserProfileEntity::class
        )

    override fun save(entity: SkillLabUserProfileEntity) {
        val now = Instant.now().atOffset(ZoneOffset.UTC)
        entity.latestSyncAt = now
        entity.importedAt = entity.importedAt ?: now

        couchDbClient.putDatabaseDocument(
            database = SKILL_USER_PROFILE_DATABASE,
            documentId = documentId(entity.externalIdentifier),
            body = entity
        )
    }

    private fun documentId(externalIdentifier: String) = skillDocumentId(DOCUMENT_PREFIX, externalIdentifier)
}
