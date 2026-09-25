package com.aamdigital.aambackendservice.common.changes

import com.aamdigital.aambackendservice.common.couchdb.core.CouchDbClient
import com.aamdigital.aambackendservice.common.couchdb.dto.CouchDbChangeResult
import com.aamdigital.aambackendservice.common.couchdb.dto.CouchDbChangesResponse
import com.aamdigital.aambackendservice.common.domain.TestErrorCode
import com.aamdigital.aambackendservice.common.error.ExternalSystemException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.*

@ExtendWith(MockitoExtension::class)
class CouchDbChangesProcessorTest {

    private lateinit var service: CouchDbChangesProcessor
    private val objectMapper = ObjectMapper()

    @Mock
    lateinit var couchDbClient: CouchDbClient

    @Mock
    lateinit var syncRepository: SyncRepository

    private class RecordingHandler(
        override val consumerName: String = "test",
        private val failure: Exception? = null
    ) : DocumentChangeHandler {
        val received = mutableListOf<DocumentChangeEvent>()

        override fun handle(event: DocumentChangeEvent) {
            received += event
            failure?.let { throw it }
        }
    }

    private val handler = RecordingHandler()

    private fun cursor(
        latestRef: String,
        consumer: String = "test"
    ) = SyncEntry(database = "app", latestRef = latestRef, consumer = consumer)

    private fun change(
        id: String,
        seq: String
    ) = CouchDbChangeResult(
        id = id,
        changes = emptyList(),
        seq = seq,
        doc = objectMapper.createObjectNode().put("_id", id).put("_rev", "1-a")
    )

    @BeforeEach
    fun setUp() {
        reset(couchDbClient, syncRepository)
        service = CouchDbChangesProcessor(
            couchDbClient = couchDbClient,
            syncRepository = syncRepository,
            objectMapper = objectMapper,
            changeDetectionProperties = ChangeDetectionProperties(includedDatabases = listOf("app")),
        )
    }

    @Test
    fun `should skip databases starting with underscore`() {
        whenever(couchDbClient.allDatabases()).thenReturn(listOf("_users", "_replicator", "app"))

        whenever(syncRepository.findByDatabase("app", "test")).thenReturn(Optional.of(cursor("seq-1")))
        whenever(couchDbClient.getDatabaseChanges(eq("app"), any()))
            .thenReturn(CouchDbChangesResponse(lastSeq = "seq-1", results = emptyList(), pending = 0))

        service.checkForChanges(handler)

        verify(couchDbClient, never()).getDatabaseChanges(eq("_users"), any())
        verify(couchDbClient, never()).getDatabaseChanges(eq("_replicator"), any())
    }

    @Test
    fun `should skip databases not on the included-databases allowlist`() {
        whenever(couchDbClient.allDatabases()).thenReturn(
            listOf("app", "audit", "notifications-x", "app-attachments")
        )

        whenever(syncRepository.findByDatabase("app", "test")).thenReturn(Optional.of(cursor("seq-1")))
        whenever(couchDbClient.getDatabaseChanges(eq("app"), any()))
            .thenReturn(CouchDbChangesResponse(lastSeq = "seq-1", results = emptyList(), pending = 0))

        service.checkForChanges(handler)

        verify(couchDbClient).getDatabaseChanges(eq("app"), any())
        verify(couchDbClient, never()).getDatabaseChanges(eq("audit"), any())
        verify(couchDbClient, never()).getDatabaseChanges(eq("notifications-x"), any())
        verify(couchDbClient, never()).getDatabaseChanges(eq("app-attachments"), any())
    }

    @Test
    fun `should poll databases added to a custom included-databases allowlist`() {
        service = CouchDbChangesProcessor(
            couchDbClient = couchDbClient,
            syncRepository = syncRepository,
            objectMapper = objectMapper,
            changeDetectionProperties = ChangeDetectionProperties(includedDatabases = listOf("app", "audit")),
        )

        whenever(couchDbClient.allDatabases()).thenReturn(listOf("app", "audit", "notifications-x"))

        whenever(syncRepository.findByDatabase(any(), eq("test"))).thenAnswer {
            Optional.of(SyncEntry(database = it.arguments[0] as String, latestRef = "seq-1", consumer = "test"))
        }
        whenever(couchDbClient.getDatabaseChanges(any(), any()))
            .thenReturn(CouchDbChangesResponse(lastSeq = "seq-1", results = emptyList(), pending = 0))

        service.checkForChanges(handler)

        verify(couchDbClient).getDatabaseChanges(eq("app"), any())
        verify(couchDbClient).getDatabaseChanges(eq("audit"), any())
        verify(couchDbClient, never()).getDatabaseChanges(eq("notifications-x"), any())
    }

    @Test
    fun `should skip design documents`() {
        whenever(couchDbClient.allDatabases()).thenReturn(listOf("app"))
        whenever(syncRepository.findByDatabase("app", "test")).thenReturn(Optional.of(cursor("seq-0")))

        val designDoc = objectMapper.createObjectNode()
            .put("_id", "_design/myview")
            .put("_rev", "1-abc")
        val changeResult = CouchDbChangeResult(
            id = "_design/myview",
            changes = emptyList(),
            seq = "seq-1",
            doc = designDoc,
        )
        whenever(couchDbClient.getDatabaseChanges(eq("app"), any()))
            .thenReturn(CouchDbChangesResponse(lastSeq = "seq-1", results = listOf(changeResult), pending = 0))
        whenever(syncRepository.save(any<SyncEntry>())).thenAnswer { it.arguments[0] }

        service.checkForChanges(handler)

        assertThat(handler.received).isEmpty()
    }

    @Test
    fun `should publish enriched event for normal document change`() {
        whenever(couchDbClient.allDatabases()).thenReturn(listOf("app"))
        whenever(syncRepository.findByDatabase("app", "test")).thenReturn(Optional.of(cursor("seq-0")))

        val currentDoc = objectMapper.createObjectNode()
            .put("_id", "Child:1")
            .put("_rev", "2-def")
            .put("name", "Alice")
        val changeResult = CouchDbChangeResult(
            id = "Child:1",
            changes = emptyList(),
            seq = "seq-1",
            doc = currentDoc,
        )
        whenever(couchDbClient.getDatabaseChanges(eq("app"), any()))
            .thenReturn(CouchDbChangesResponse(lastSeq = "seq-1", results = listOf(changeResult), pending = 0))

        val previousDoc = objectMapper.createObjectNode()
            .put("_id", "Child:1")
            .put("_rev", "1-abc")
            .put("name", "Bob")
        whenever(couchDbClient.getPreviousDocumentRevision(eq("app"), eq("Child:1"), eq("2-def"), eq(ObjectNode::class)))
            .thenReturn(Optional.of(previousDoc))

        whenever(syncRepository.save(any<SyncEntry>())).thenAnswer { it.arguments[0] }

        service.checkForChanges(handler)

        assertThat(handler.received).singleElement().satisfies({ event ->
            assertThat(event.database).isEqualTo("app")
            assertThat(event.documentId).isEqualTo("Child:1")
            assertThat(event.rev).isEqualTo("2-def")
            assertThat(event.deleted).isFalse()
            assertThat(event.currentVersion["name"]).isEqualTo("Alice")
            assertThat(event.previousVersion["name"]).isEqualTo("Bob")
        })
    }

    @Test
    fun `should publish deleted event with empty versions for deleted document`() {
        whenever(couchDbClient.allDatabases()).thenReturn(listOf("app"))
        whenever(syncRepository.findByDatabase("app", "test")).thenReturn(Optional.of(cursor("seq-0")))

        val deletedDoc = objectMapper.createObjectNode()
            .put("_id", "Child:2")
            .put("_rev", "3-xyz")
            .put("_deleted", true)
        val changeResult = CouchDbChangeResult(
            id = "Child:2",
            changes = emptyList(),
            seq = "seq-2",
            doc = deletedDoc,
            deleted = true,
        )
        whenever(couchDbClient.getDatabaseChanges(eq("app"), any()))
            .thenReturn(CouchDbChangesResponse(lastSeq = "seq-2", results = listOf(changeResult), pending = 0))
        whenever(syncRepository.save(any<SyncEntry>())).thenAnswer { it.arguments[0] }

        service.checkForChanges(handler)

        assertThat(handler.received).singleElement().satisfies({ event ->
            assertThat(event.deleted).isTrue()
            assertThat(event.documentId).isEqualTo("Child:2")
            assertThat(event.currentVersion).isEmpty()
            assertThat(event.previousVersion).isEmpty()
        })
    }

    @Test
    fun `should use empty object as previous version when revision lookup fails`() {
        whenever(couchDbClient.allDatabases()).thenReturn(listOf("app"))
        whenever(syncRepository.findByDatabase("app", "test")).thenReturn(Optional.of(cursor("seq-0")))

        val currentDoc = objectMapper.createObjectNode()
            .put("_id", "Child:3")
            .put("_rev", "1-first")
        val changeResult = CouchDbChangeResult(
            id = "Child:3",
            changes = emptyList(),
            seq = "seq-3",
            doc = currentDoc,
        )
        whenever(couchDbClient.getDatabaseChanges(eq("app"), any()))
            .thenReturn(CouchDbChangesResponse(lastSeq = "seq-3", results = listOf(changeResult), pending = 0))

        val testErrorCode = TestErrorCode.TEST_EXCEPTION
        whenever(couchDbClient.getPreviousDocumentRevision(eq("app"), eq("Child:3"), eq("1-first"), eq(ObjectNode::class)))
            .thenAnswer {
                throw ExternalSystemException(message = "rev not available", code = testErrorCode)
            }
        whenever(syncRepository.save(any<SyncEntry>())).thenAnswer { it.arguments[0] }

        service.checkForChanges(handler)

        assertThat(handler.received).singleElement().satisfies({ event ->
            assertThat(event.deleted).isFalse()
            assertThat(event.documentId).isEqualTo("Child:3")
            assertThat(event.previousVersion).isEmpty()
        })
    }

    @Test
    fun `should not rewrite the sync entry when no changes were processed`() {
        whenever(couchDbClient.allDatabases()).thenReturn(listOf("app"))
        whenever(syncRepository.findByDatabase("app", "test")).thenReturn(Optional.of(cursor("seq-0")))
        whenever(couchDbClient.getDatabaseChanges(eq("app"), any()))
            .thenReturn(CouchDbChangesResponse(lastSeq = "seq-5", results = emptyList(), pending = 0))

        service.checkForChanges(handler)

        verify(syncRepository, never()).save(any())
    }

    @Test
    fun `should start a consumer without a cursor from now and store that cursor even without changes`() {
        whenever(couchDbClient.allDatabases()).thenReturn(listOf("app"))
        whenever(syncRepository.findByDatabase("app", "test")).thenReturn(Optional.empty())
        whenever(couchDbClient.getDatabaseDocument(eq("app"), eq(""), any(), eq(ObjectNode::class)))
            .thenReturn(objectMapper.createObjectNode().put("update_seq", "seq-now"))
        whenever(couchDbClient.getDatabaseChanges(eq("app"), any()))
            .thenReturn(CouchDbChangesResponse(lastSeq = "seq-now", results = emptyList(), pending = 0))
        whenever(syncRepository.save(any<SyncEntry>())).thenAnswer { it.arguments[0] }

        service.checkForChanges(handler)

        verify(syncRepository).save(eq(cursor("seq-now")))
    }

    @Test
    fun `should advance latestRef once per change rather than once per batch`() {
        // a crash part way through a batch then re-processes only the change that was in flight,
        // instead of everything the batch had already handled
        whenever(couchDbClient.allDatabases()).thenReturn(listOf("app"))
        whenever(syncRepository.findByDatabase("app", "test")).thenReturn(Optional.of(cursor("seq-0")))

        whenever(couchDbClient.getDatabaseChanges(eq("app"), any()))
            .thenReturn(
                CouchDbChangesResponse(
                    lastSeq = "seq-5",
                    results = listOf(change("X:1", "seq-1"), change("X:2", "seq-2")),
                    pending = 0
                )
            )
        whenever(couchDbClient.getPreviousDocumentRevision(any(), any(), any(), eq(ObjectNode::class)))
            .thenReturn(Optional.of(objectMapper.createObjectNode()))
        whenever(syncRepository.save(any<SyncEntry>())).thenAnswer { it.arguments[0] }

        service.checkForChanges(handler)

        val saved = inOrder(syncRepository)
        saved.verify(syncRepository).save(eq(cursor("seq-1")))
        saved.verify(syncRepository).save(eq(cursor("seq-2")))
        verify(syncRepository, times(2)).save(any())
    }

    @Test
    fun `should read and advance only the cursor of the consumer it polls for`() {
        // each module moves through the feed at its own pace, so polling for one consumer must
        // neither start from nor move another consumer's position
        whenever(couchDbClient.allDatabases()).thenReturn(listOf("app"))
        whenever(syncRepository.findByDatabase("app", "reporting"))
            .thenReturn(Optional.of(cursor("seq-0", consumer = "reporting")))
        whenever(couchDbClient.getDatabaseChanges(eq("app"), any()))
            .thenReturn(
                CouchDbChangesResponse(lastSeq = "seq-1", results = listOf(change("X:1", "seq-1")), pending = 0)
            )
        whenever(couchDbClient.getPreviousDocumentRevision(any(), any(), any(), eq(ObjectNode::class)))
            .thenReturn(Optional.of(objectMapper.createObjectNode()))
        whenever(syncRepository.save(any<SyncEntry>())).thenAnswer { it.arguments[0] }

        service.checkForChanges(RecordingHandler(consumerName = "reporting"))

        verify(couchDbClient).getDatabaseChanges(eq("app"), argThat { getFirst("last-event-id") == "seq-0" })
        verify(syncRepository).save(eq(cursor("seq-1", consumer = "reporting")))
        verify(syncRepository, never()).findByDatabase(any(), eq("notification"))
    }

    @Test
    fun `should advance the cursor past a change the handler failed on`() {
        // the handler owns its recovery: its feed must not stall on one document it cannot handle
        whenever(couchDbClient.allDatabases()).thenReturn(listOf("app"))
        whenever(syncRepository.findByDatabase("app", "test")).thenReturn(Optional.of(cursor("seq-0")))
        val failingHandler = RecordingHandler(failure = RuntimeException("handler exploded"))

        whenever(couchDbClient.getDatabaseChanges(eq("app"), any()))
            .thenReturn(
                CouchDbChangesResponse(
                    lastSeq = "seq-2",
                    results = listOf(change("X:1", "seq-1"), change("X:2", "seq-2")),
                    pending = 0
                )
            )
        whenever(couchDbClient.getPreviousDocumentRevision(any(), any(), any(), eq(ObjectNode::class)))
            .thenReturn(Optional.of(objectMapper.createObjectNode()))
        whenever(syncRepository.save(any<SyncEntry>())).thenAnswer { it.arguments[0] }

        service.checkForChanges(failingHandler)

        assertThat(failingHandler.received.map { it.documentId }).containsExactly("X:1", "X:2")
        verify(syncRepository).save(eq(cursor("seq-2")))
    }
}
