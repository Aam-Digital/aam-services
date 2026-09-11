package com.aamdigital.aambackendservice.thirdpartyauthentication.repository

import com.aamdigital.aambackendservice.common.couchdb.core.BACKEND_STATE_DATABASE
import com.aamdigital.aambackendservice.common.couchdb.core.CouchDbClient
import com.aamdigital.aambackendservice.common.error.NotFoundException

/**
 * [ThirdPartyAuthSessionRepository] backed by one CouchDB document per session.
 *
 * Keyed by sessionId rather than by user because the redirect lookup has to tell "this session
 * belongs to someone else" apart from "no such session", which a per-user document could not.
 * Document count grows with the number of SSO logins, exactly as the PostgreSQL table it replaces
 * did - neither is pruned today.
 */
class CouchDbThirdPartyAuthSessionRepository(
    private val couchDbClient: CouchDbClient
) : ThirdPartyAuthSessionRepository {
    companion object {
        const val DOCUMENT_PREFIX = "ThirdPartyAuthSession"
    }

    override fun findBySessionId(sessionId: String): ThirdPartyAuthSession? =
        try {
            couchDbClient.getDatabaseDocument(
                database = BACKEND_STATE_DATABASE,
                documentId = documentId(sessionId),
                kClass = ThirdPartyAuthSession::class
            )
        } catch (_: NotFoundException) {
            null
        }

    override fun save(session: ThirdPartyAuthSession) {
        couchDbClient.putDatabaseDocument(
            database = BACKEND_STATE_DATABASE,
            documentId = documentId(session.sessionId),
            body = session
        )
    }

    private fun documentId(sessionId: String) = "$DOCUMENT_PREFIX:$sessionId"
}
