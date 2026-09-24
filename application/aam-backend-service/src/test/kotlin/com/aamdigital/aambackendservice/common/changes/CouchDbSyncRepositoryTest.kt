package com.aamdigital.aambackendservice.common.changes

import com.aamdigital.aambackendservice.common.couchdb.core.BACKEND_STATE_DATABASE
import com.aamdigital.aambackendservice.common.couchdb.core.CouchDbClient
import com.aamdigital.aambackendservice.common.domain.TestErrorCode
import com.aamdigital.aambackendservice.common.error.ExternalSystemException
import com.aamdigital.aambackendservice.common.error.NotFoundException
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * "No cursor" makes the poll start from "now", skipping everything in between, so it must only be
 * reported when the cursor document is really absent.
 */
class CouchDbSyncRepositoryTest {
    private val couchDbClient = mock<CouchDbClient>()
    private val repository = CouchDbSyncRepository(couchDbClient, ObjectMapper())

    @BeforeEach
    fun setUp() {
        // the client reports every 4xx on a document read as NotFoundException
        whenever(
            couchDbClient.getDatabaseDocument(
                eq(BACKEND_STATE_DATABASE),
                eq("SyncEntry:app"),
                any(),
                eq(SyncEntry::class)
            )
        ).thenThrow(NotFoundException(code = TestErrorCode.TEST_EXCEPTION))
    }

    @Test
    fun `finds nothing when the cursor document does not exist`() {
        whenever(couchDbClient.databaseExists(BACKEND_STATE_DATABASE)).thenReturn(true)

        assertThat(repository.findByDatabase("app")).isEmpty()
    }

    @Test
    fun `fails when the state database does not exist`() {
        whenever(couchDbClient.databaseExists(BACKEND_STATE_DATABASE)).thenReturn(false)

        assertThatThrownBy { repository.findByDatabase("app") }.isInstanceOf(ExternalSystemException::class.java)
    }

    @Test
    fun `fails when the state database cannot be read`() {
        whenever(couchDbClient.databaseExists(BACKEND_STATE_DATABASE))
            .thenAnswer { throw ExternalSystemException(code = TestErrorCode.TEST_EXCEPTION) }

        assertThatThrownBy { repository.findByDatabase("app") }.isInstanceOf(ExternalSystemException::class.java)
    }
}
