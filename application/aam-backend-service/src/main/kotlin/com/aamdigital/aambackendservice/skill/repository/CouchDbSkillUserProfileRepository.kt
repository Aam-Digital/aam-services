package com.aamdigital.aambackendservice.skill.repository

import com.aamdigital.aambackendservice.common.couchdb.core.CouchDbClient
import com.aamdigital.aambackendservice.common.couchdb.core.fetchAllDocumentsByPrefix
import com.aamdigital.aambackendservice.common.error.NotFoundException
import com.fasterxml.jackson.databind.ObjectMapper
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * [SkillUserProfileRepository] backed by one CouchDB document per profile, in a database of its
 * own so the mirror can be dropped and rebuilt without touching anything else.
 */
class CouchDbSkillUserProfileRepository(
    private val couchDbClient: CouchDbClient,
    private val objectMapper: ObjectMapper
) : SkillUserProfileRepository {
    companion object {
        const val SKILL_USER_PROFILE_DATABASE = "skill-user-profile"
        const val DOCUMENT_PREFIX = "SkillProfile"
    }

    override fun findByExternalIdentifier(externalIdentifier: String): SkillUserProfile? =
        try {
            couchDbClient.getDatabaseDocument(
                database = SKILL_USER_PROFILE_DATABASE,
                documentId = documentId(externalIdentifier),
                kClass = SkillUserProfile::class
            )
        } catch (_: NotFoundException) {
            null
        }

    override fun findAll(): List<SkillUserProfile> =
        fetchAllDocumentsByPrefix(
            couchDbClient = couchDbClient,
            objectMapper = objectMapper,
            database = SKILL_USER_PROFILE_DATABASE,
            prefix = DOCUMENT_PREFIX,
            kClass = SkillUserProfile::class
        )

    override fun save(profile: SkillUserProfile) {
        couchDbClient.putDatabaseDocument(
            database = SKILL_USER_PROFILE_DATABASE,
            documentId = documentId(profile.externalIdentifier),
            body = profile
        )
    }

    /** The identifier comes from a third party, so it is encoded rather than trusted as a doc id. */
    private fun documentId(externalIdentifier: String) =
        "$DOCUMENT_PREFIX:${URLEncoder.encode(externalIdentifier, StandardCharsets.UTF_8)}"
}
