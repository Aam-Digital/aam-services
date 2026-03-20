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
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
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
    lateinit var changeEventPublisher: ChangeEventPublisher

    @Mock
    lateinit var syncRepository: SyncRepository

    @BeforeEach
    fun setUp() {
        reset(couchDbClient, changeEventPublisher, syncRepository)
        service = CouchDbChangesProcessor(
            couchDbClient = couchDbClient,
            documentChangeEventPublisher = changeEventPublisher,
            syncRepository = syncRepository,
            objectMapper = objectMapper,
        )
    }

    @Test
    fun `should skip databases starting with underscore`() {
        whenever(couchDbClient.allDatabases()).thenReturn(listOf("_users", "_replicator", "app"))

        whenever(syncRepository.findByDatabase("app")).thenReturn(
            Optional.of(SyncEntry(database = "app", latestRef = "seq-1"))
        )
        whenever(couchDbClient.getDatabaseChanges(eq("app"), any()))
            .thenReturn(CouchDbChangesResponse(lastSeq = "seq-1", results = emptyList(), pending = 0))
        whenever(syncRepository.save(any<SyncEntry>())).thenAnswer { it.arguments[0] }

        service.checkForChanges()

        verify(couchDbClient, never()).getDatabaseChanges(eq("_users"), any())
        verify(couchDbClient, never()).getDatabaseChanges(eq("_replicator"), any())
    }

    @Test
    fun `should skip design documents`() {
        whenever(couchDbClient.allDatabases()).thenReturn(listOf("app"))
        whenever(syncRepository.findByDatabase("app")).thenReturn(
            Optional.of(SyncEntry(database = "app", latestRef = "seq-0"))
        )

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

        service.checkForChanges()

        verify(changeEventPublisher, never()).publish(any(), any())
    }

    @Test
    fun `should publish enriched event for normal document change`() {
        whenever(couchDbClient.allDatabases()).thenReturn(listOf("app"))
        whenever(syncRepository.findByDatabase("app")).thenReturn(
            Optional.of(SyncEntry(database = "app", latestRef = "seq-0"))
        )

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

        service.checkForChanges()

        verify(changeEventPublisher).publish(
            eq(ChangesQueueConfiguration.DOCUMENT_CHANGES_EXCHANGE),
            argThat { event: DocumentChangeEvent ->
                event.database == "app" &&
                    event.documentId == "Child:1" &&
                    event.rev == "2-def" &&
                    !event.deleted &&
                    (event.currentVersion["name"] == "Alice") &&
                    (event.previousVersion["name"] == "Bob")
            }
        )
    }

    @Test
    fun `should publish deleted event with empty versions for deleted document`() {
        whenever(couchDbClient.allDatabases()).thenReturn(listOf("app"))
        whenever(syncRepository.findByDatabase("app")).thenReturn(
            Optional.of(SyncEntry(database = "app", latestRef = "seq-0"))
        )

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

        service.checkForChanges()

        verify(changeEventPublisher).publish(
            eq(ChangesQueueConfiguration.DOCUMENT_CHANGES_EXCHANGE),
            argThat { event: DocumentChangeEvent ->
                event.deleted &&
                    event.documentId == "Child:2" &&
                    event.currentVersion.isEmpty() &&
                    event.previousVersion.isEmpty()
            }
        )
    }

    @Test
    fun `should use empty object as previous version when revision lookup fails`() {
        whenever(couchDbClient.allDatabases()).thenReturn(listOf("app"))
        whenever(syncRepository.findByDatabase("app")).thenReturn(
            Optional.of(SyncEntry(database = "app", latestRef = "seq-0"))
        )

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

        service.checkForChanges()

        verify(changeEventPublisher).publish(
            eq(ChangesQueueConfiguration.DOCUMENT_CHANGES_EXCHANGE),
            argThat { event: DocumentChangeEvent ->
                !event.deleted &&
                    event.documentId == "Child:3" &&
                    event.previousVersion.isEmpty()
            }
        )
    }

    @Test
    fun `should update sync entry with latest seq after processing`() {
        whenever(couchDbClient.allDatabases()).thenReturn(listOf("app"))
        val existingSync = SyncEntry(id = 1, database = "app", latestRef = "seq-0")
        whenever(syncRepository.findByDatabase("app")).thenReturn(Optional.of(existingSync))
        whenever(couchDbClient.getDatabaseChanges(eq("app"), any()))
            .thenReturn(CouchDbChangesResponse(lastSeq = "seq-5", results = emptyList(), pending = 0))
        whenever(syncRepository.save(any<SyncEntry>())).thenAnswer { it.arguments[0] }

        service.checkForChanges()

        verify(syncRepository).save(argThat<SyncEntry> { latestRef == "seq-0" })
    }

    @Test
    fun `should update latestRef to last result seq when results are present`() {
        whenever(couchDbClient.allDatabases()).thenReturn(listOf("app"))
        whenever(syncRepository.findByDatabase("app")).thenReturn(
            Optional.of(SyncEntry(database = "app", latestRef = "seq-0"))
        )

        val doc = objectMapper.createObjectNode().put("_id", "X:1").put("_rev", "1-a")
        val results = listOf(
            CouchDbChangeResult(id = "X:1", changes = emptyList(), seq = "seq-1", doc = doc),
            CouchDbChangeResult(id = "X:2", changes = emptyList(), seq = "seq-2", doc = doc),
        )
        whenever(couchDbClient.getDatabaseChanges(eq("app"), any()))
            .thenReturn(CouchDbChangesResponse(lastSeq = "seq-5", results = results, pending = 0))
        whenever(couchDbClient.getPreviousDocumentRevision(any(), any(), any(), eq(ObjectNode::class)))
            .thenReturn(Optional.of(objectMapper.createObjectNode()))
        whenever(syncRepository.save(any<SyncEntry>())).thenAnswer { it.arguments[0] }

        service.checkForChanges()

        verify(syncRepository).save(argThat<SyncEntry> { latestRef == "seq-2" })
    }
}
