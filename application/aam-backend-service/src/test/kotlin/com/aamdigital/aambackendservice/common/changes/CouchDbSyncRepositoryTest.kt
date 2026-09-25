package com.aamdigital.aambackendservice.common.changes

import com.aamdigital.aambackendservice.common.couchdb.core.BACKEND_STATE_DATABASE
import com.aamdigital.aambackendservice.common.couchdb.core.CouchDbClient
import com.aamdigital.aambackendservice.common.couchdb.dto.DocSuccess
import com.aamdigital.aambackendservice.common.domain.TestErrorCode
import com.aamdigital.aambackendservice.common.error.ExternalSystemException
import com.aamdigital.aambackendservice.common.error.NotFoundException
import com.aamdigital.aambackendservice.common.rest.ObjectMapperConfiguration
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
    private val repository = CouchDbSyncRepository(couchDbClient)

    private fun stubStoredCursor(documentId: String = "SyncEntry:app:reporting") =
        whenever(
            couchDbClient.getDatabaseDocument(
                eq(BACKEND_STATE_DATABASE),
                eq(documentId),
                any(),
                eq(SyncEntryDocument::class)
            )
        )

    private fun stubSave(
        rev: String,
        documentId: String = "SyncEntry:app:reporting"
    ) = whenever(
        couchDbClient.putDatabaseDocumentAtRevision(
            eq(BACKEND_STATE_DATABASE),
            eq(documentId),
            any(),
            anyOrNull()
        )
    ).thenReturn(DocSuccess(ok = true, id = documentId, rev = rev))

    private fun cursor(latestRef: String) = SyncEntry(database = "app", latestRef = latestRef, consumer = "reporting")

    private fun cursorDocument(
        latestRef: String,
        rev: String
    ) = SyncEntryDocument(database = "app", latestRef = latestRef, consumer = "reporting", rev = rev)

    @Test
    fun `finds nothing when the cursor document does not exist`() {
        // the client reports every 4xx on a document read as NotFoundException
        stubStoredCursor().thenThrow(NotFoundException(code = TestErrorCode.TEST_EXCEPTION))
        whenever(couchDbClient.databaseExists(BACKEND_STATE_DATABASE)).thenReturn(true)

        assertThat(repository.findByDatabase("app", "reporting")).isEmpty()
    }

    @Test
    fun `fails when the state database does not exist`() {
        stubStoredCursor().thenThrow(NotFoundException(code = TestErrorCode.TEST_EXCEPTION))
        whenever(couchDbClient.databaseExists(BACKEND_STATE_DATABASE)).thenReturn(false)

        assertThatThrownBy { repository.findByDatabase("app", "reporting") }
            .isInstanceOf(ExternalSystemException::class.java)
    }

    @Test
    fun `fails when the state database cannot be read`() {
        stubStoredCursor().thenThrow(NotFoundException(code = TestErrorCode.TEST_EXCEPTION))
        whenever(couchDbClient.databaseExists(BACKEND_STATE_DATABASE))
            .thenAnswer { throw ExternalSystemException(code = TestErrorCode.TEST_EXCEPTION) }

        assertThatThrownBy { repository.findByDatabase("app", "reporting") }
            .isInstanceOf(ExternalSystemException::class.java)
    }

    /** Polled every few seconds: one read per database, and no revision lookup before a write. */
    @Test
    fun `reads a cursor once and writes it at the revision it last saw`() {
        stubStoredCursor().thenReturn(cursorDocument("seq-1", "1-a"))
        stubSave("2-b")

        assertThat(repository.findByDatabase("app", "reporting")).contains(cursor("seq-1"))
        repository.save(cursor("seq-2"))
        assertThat(repository.findByDatabase("app", "reporting")).contains(cursor("seq-2"))
        repository.save(cursor("seq-3"))

        verify(couchDbClient, times(1)).getDatabaseDocument(any(), any(), any(), eq(SyncEntryDocument::class))
        verify(couchDbClient).putDatabaseDocumentAtRevision(any(), any(), eq(cursor("seq-2")), eq("1-a"))
        verify(couchDbClient).putDatabaseDocumentAtRevision(any(), any(), eq(cursor("seq-3")), eq("2-b"))
        verify(couchDbClient, never()).headDatabaseDocument(any(), any())
    }

    @Test
    fun `creates a cursor that was not found`() {
        stubStoredCursor().thenThrow(NotFoundException(code = TestErrorCode.TEST_EXCEPTION))
        whenever(couchDbClient.databaseExists(BACKEND_STATE_DATABASE)).thenReturn(true)
        stubSave("1-a")

        assertThat(repository.findByDatabase("app", "reporting")).isEmpty()
        repository.save(cursor("seq-1"))

        verify(couchDbClient).putDatabaseDocumentAtRevision(any(), any(), eq(cursor("seq-1")), isNull())
    }

    /** A cursor reset by hand in CouchDB makes the next write fail; it must then be read again. */
    @Test
    fun `reads the cursor again after a failed write`() {
        stubStoredCursor()
            .thenReturn(cursorDocument("seq-1", "1-a"))
            .thenReturn(cursorDocument("seq-0", "2-reset"))
        whenever(couchDbClient.putDatabaseDocumentAtRevision(any(), any(), any(), anyOrNull()))
            .thenThrow(ExternalSystemException(code = TestErrorCode.TEST_EXCEPTION))

        repository.findByDatabase("app", "reporting")
        assertThatThrownBy { repository.save(cursor("seq-2")) }
            .isInstanceOf(ExternalSystemException::class.java)

        assertThat(repository.findByDatabase("app", "reporting")).contains(cursor("seq-0"))
    }

    /** Every restart reads the stored cursor back; a mapping error there would stop change detection. */
    @Test
    fun `reads a cursor document as CouchDB returns it`() {
        val json =
            """{"_id":"SyncEntry:app:reporting","_rev":"1-a","database":"app","latestRef":"seq-1","consumer":"reporting"}"""

        val document = ObjectMapperConfiguration().objectMapper().readValue(json, SyncEntryDocument::class.java)

        assertThat(document).isEqualTo(cursorDocument("seq-1", "1-a"))
    }

    /** Both consumers of a database write their own document, each with its own revision. */
    @Test
    fun `keeps the revision of every consumer's cursor apart`() {
        stubStoredCursor("SyncEntry:app:reporting").thenReturn(cursorDocument("seq-1", "1-r"))
        stubStoredCursor("SyncEntry:app:notification").thenReturn(
            SyncEntryDocument(database = "app", latestRef = "seq-1", consumer = "notification", rev = "1-n")
        )
        stubSave("2-r", "SyncEntry:app:reporting")
        stubSave("2-n", "SyncEntry:app:notification")

        repository.findByDatabase("app", "reporting")
        repository.findByDatabase("app", "notification")
        repository.save(cursor("seq-2"))
        repository.save(SyncEntry(database = "app", latestRef = "seq-2", consumer = "notification"))

        verify(couchDbClient).putDatabaseDocumentAtRevision(any(), eq("SyncEntry:app:reporting"), any(), eq("1-r"))
        verify(couchDbClient).putDatabaseDocumentAtRevision(any(), eq("SyncEntry:app:notification"), any(), eq("1-n"))
    }

    @Test
    fun `keeps the cursor from before consumers had their own under its old id`() {
        stubStoredCursor("SyncEntry:app")
            .thenReturn(SyncEntryDocument(database = "app", latestRef = "seq-shared", rev = "7-s"))

        assertThat(repository.findByDatabase("app", consumer = null)).contains(SyncEntry("app", "seq-shared"))
    }

    @Test
    fun `deletes a cursor and reads it afresh afterwards`() {
        stubStoredCursor("SyncEntry:app")
            .thenReturn(SyncEntryDocument(database = "app", latestRef = "seq-shared", rev = "7-s"))
            .thenThrow(NotFoundException(code = TestErrorCode.TEST_EXCEPTION))
        whenever(couchDbClient.databaseExists(BACKEND_STATE_DATABASE)).thenReturn(true)
        val shared = repository.findByDatabase("app", consumer = null).get()

        repository.delete(shared)

        verify(couchDbClient).deleteDatabaseDocument(BACKEND_STATE_DATABASE, "SyncEntry:app")
        assertThat(repository.findByDatabase("app", consumer = null)).isEmpty()
    }
}
