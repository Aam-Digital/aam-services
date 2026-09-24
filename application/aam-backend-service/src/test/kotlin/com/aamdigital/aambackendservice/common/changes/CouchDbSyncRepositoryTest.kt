package com.aamdigital.aambackendservice.common.changes

import com.aamdigital.aambackendservice.common.couchdb.core.BACKEND_STATE_DATABASE
import com.aamdigital.aambackendservice.common.couchdb.core.CouchDbClient
import com.aamdigital.aambackendservice.common.couchdb.dto.DocSuccess
import com.aamdigital.aambackendservice.common.domain.TestErrorCode
import com.aamdigital.aambackendservice.common.error.ExternalSystemException
import com.aamdigital.aambackendservice.common.error.NotFoundException
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * "No cursor" makes the poll start from "now", skipping everything in between, so it must only be
 * reported when the cursor document is really absent.
 */
class CouchDbSyncRepositoryTest {
    private val couchDbClient = mock<CouchDbClient>()
    private val repository = CouchDbSyncRepository(couchDbClient, ObjectMapper())

    private fun stubStoredCursor() =
        whenever(
            couchDbClient.getDatabaseDocument(
                eq(BACKEND_STATE_DATABASE),
                eq("SyncEntry:app"),
                any(),
                eq(SyncEntryDocument::class)
            )
        )

    private fun stubSave(rev: String) =
        whenever(
            couchDbClient.putDatabaseDocumentAtRevision(
                eq(BACKEND_STATE_DATABASE),
                eq("SyncEntry:app"),
                any(),
                anyOrNull()
            )
        ).thenReturn(DocSuccess(ok = true, id = "SyncEntry:app", rev = rev))

    @Test
    fun `finds nothing when the cursor document does not exist`() {
        // the client reports every 4xx on a document read as NotFoundException
        stubStoredCursor().thenThrow(NotFoundException(code = TestErrorCode.TEST_EXCEPTION))
        whenever(couchDbClient.databaseExists(BACKEND_STATE_DATABASE)).thenReturn(true)

        assertThat(repository.findByDatabase("app")).isEmpty()
    }

    @Test
    fun `fails when the state database does not exist`() {
        stubStoredCursor().thenThrow(NotFoundException(code = TestErrorCode.TEST_EXCEPTION))
        whenever(couchDbClient.databaseExists(BACKEND_STATE_DATABASE)).thenReturn(false)

        assertThatThrownBy { repository.findByDatabase("app") }.isInstanceOf(ExternalSystemException::class.java)
    }

    @Test
    fun `fails when the state database cannot be read`() {
        stubStoredCursor().thenThrow(NotFoundException(code = TestErrorCode.TEST_EXCEPTION))
        whenever(couchDbClient.databaseExists(BACKEND_STATE_DATABASE))
            .thenAnswer { throw ExternalSystemException(code = TestErrorCode.TEST_EXCEPTION) }

        assertThatThrownBy { repository.findByDatabase("app") }.isInstanceOf(ExternalSystemException::class.java)
    }

    /** Polled every few seconds: one read per database, and no revision lookup before a write. */
    @Test
    fun `reads a cursor once and writes it at the revision it last saw`() {
        stubStoredCursor().thenReturn(SyncEntryDocument(database = "app", latestRef = "seq-1", rev = "1-a"))
        stubSave("2-b")

        assertThat(repository.findByDatabase("app")).contains(SyncEntry("app", "seq-1"))
        repository.save(SyncEntry("app", "seq-2"))
        assertThat(repository.findByDatabase("app")).contains(SyncEntry("app", "seq-2"))
        repository.save(SyncEntry("app", "seq-3"))

        verify(couchDbClient, times(1)).getDatabaseDocument(any(), any(), any(), eq(SyncEntryDocument::class))
        verify(couchDbClient).putDatabaseDocumentAtRevision(any(), any(), eq(SyncEntry("app", "seq-2")), eq("1-a"))
        verify(couchDbClient).putDatabaseDocumentAtRevision(any(), any(), eq(SyncEntry("app", "seq-3")), eq("2-b"))
        verify(couchDbClient, never()).headDatabaseDocument(any(), any())
    }

    @Test
    fun `creates a cursor that was not found`() {
        stubStoredCursor().thenThrow(NotFoundException(code = TestErrorCode.TEST_EXCEPTION))
        whenever(couchDbClient.databaseExists(BACKEND_STATE_DATABASE)).thenReturn(true)
        stubSave("1-a")

        assertThat(repository.findByDatabase("app")).isEmpty()
        repository.save(SyncEntry("app", "seq-1"))

        verify(couchDbClient).putDatabaseDocumentAtRevision(any(), any(), eq(SyncEntry("app", "seq-1")), isNull())
    }

    /** A cursor reset by hand in CouchDB makes the next write fail; it must then be read again. */
    @Test
    fun `reads the cursor again after a failed write`() {
        stubStoredCursor()
            .thenReturn(SyncEntryDocument(database = "app", latestRef = "seq-1", rev = "1-a"))
            .thenReturn(SyncEntryDocument(database = "app", latestRef = "seq-0", rev = "2-reset"))
        whenever(couchDbClient.putDatabaseDocumentAtRevision(any(), any(), any(), anyOrNull()))
            .thenThrow(ExternalSystemException(code = TestErrorCode.TEST_EXCEPTION))

        repository.findByDatabase("app")
        assertThatThrownBy { repository.save(SyncEntry("app", "seq-2")) }
            .isInstanceOf(ExternalSystemException::class.java)

        assertThat(repository.findByDatabase("app")).contains(SyncEntry("app", "seq-0"))
    }
}
