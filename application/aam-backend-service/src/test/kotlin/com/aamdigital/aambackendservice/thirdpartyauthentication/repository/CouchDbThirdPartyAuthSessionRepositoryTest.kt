package com.aamdigital.aambackendservice.thirdpartyauthentication.repository

import com.aamdigital.aambackendservice.common.couchdb.core.BACKEND_STATE_DATABASE
import com.aamdigital.aambackendservice.common.couchdb.core.CouchDbClient
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

class CouchDbThirdPartyAuthSessionRepositoryTest {
    private val couchDbClient = mock<CouchDbClient>()
    private val repository = CouchDbThirdPartyAuthSessionRepository(couchDbClient)

    /** Every session id is new, so there is no revision to look up before the write. */
    @Test
    fun `creates the session document without looking up a revision first`() {
        val session = ThirdPartyAuthSession(sessionId = "session-1", userId = "user-1", redirectUrl = null)

        repository.save(session)

        verify(couchDbClient).putDatabaseDocumentAtRevision(
            eq(BACKEND_STATE_DATABASE),
            eq("ThirdPartyAuthSession:session-1"),
            eq(session),
            isNull()
        )
        verify(couchDbClient, never()).headDatabaseDocument(any(), any())
    }
}
