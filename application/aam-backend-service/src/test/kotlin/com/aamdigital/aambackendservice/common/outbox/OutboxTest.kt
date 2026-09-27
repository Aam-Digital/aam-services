package com.aamdigital.aambackendservice.common.outbox

import com.aamdigital.aambackendservice.common.couchdb.core.CouchDbClient
import com.aamdigital.aambackendservice.common.couchdb.core.CouchDbInitializer
import com.aamdigital.aambackendservice.common.error.AamErrorCode
import com.aamdigital.aambackendservice.common.error.NotFoundException
import com.aamdigital.aambackendservice.common.rest.ObjectMapperConfiguration
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpHeaders
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class OutboxTest {
    enum class Priority { LOW, HIGH }

    data class TestPayload(
        val value: String,
        val priority: Priority,
        val due: Instant
    )

    private enum class TestErrorCode : AamErrorCode { NOT_FOUND }

    private val database = "test-outbox"
    private val now: Instant = Instant.parse("2026-01-01T00:00:00Z")
    private val payload = TestPayload(value = "payload-1", priority = Priority.HIGH, due = now)

    private val couchDbClient = mock<CouchDbClient>()
    private val couchDbInitializer = mock<CouchDbInitializer>()
    private val handler = mock<OutboxHandler<TestPayload>>()
    private val objectMapper = ObjectMapperConfiguration().objectMapper()

    private val outbox =
        Outbox(
            database = database,
            payloadType = TestPayload::class,
            couchDbClient = couchDbClient,
            couchDbInitializer = couchDbInitializer,
            objectMapper = objectMapper,
            clock = Clock.fixed(now, ZoneOffset.UTC)
        )

    private fun stubExisting(eTag: String?) {
        val headers = HttpHeaders()
        eTag?.let { headers.eTag = it }
        whenever(couchDbClient.headDatabaseDocument(eq(database), eq("OutboxEntry:key-1"))).thenReturn(headers)
    }

    private fun storedBody(): Any {
        val captor = argumentCaptor<Any>()
        verify(couchDbClient).putDatabaseDocument(eq(database), eq("OutboxEntry:key-1"), captor.capture())
        return captor.firstValue
    }

    @Test
    fun `should store a new entry that is due right away`() {
        // Given
        stubExisting(eTag = null)

        // When
        val stored = outbox.enqueue("key-1", payload)

        // Then
        assertThat(stored).isTrue()
        assertThat(storedBody())
            .isEqualTo(
                OutboxEntry(id = "OutboxEntry:key-1", payload = payload, nextAttemptAt = now, createdAt = now)
            )
    }

    @Test
    fun `should leave a waiting entry alone when the same key is enqueued again`() {
        // Given overwriting would reset the entry's attempts, and it would be retried forever
        stubExisting(eTag = "\"1-abc\"")

        // When
        val stored = outbox.enqueue("key-1", payload)

        // Then
        assertThat(stored).isFalse()
        verify(couchDbClient, never()).putDatabaseDocument(any(), any(), any())
    }

    @Test
    fun `should read stored entries back with their payload type`() {
        // Given the payload type is erased at runtime, so a naive read would produce a map
        stubExisting(eTag = null)
        outbox.enqueue("key-1", payload)
        val document =
            objectMapper.valueToTree<ObjectNode>(storedBody()).put("_rev", "1-abc")
        whenever(couchDbClient.getDatabaseDocumentsByPrefix(eq(database), eq("OutboxEntry"), eq(JsonNode::class)))
            .thenReturn(listOf(document))

        // When
        val entries = outbox.fetchAll()

        // Then
        assertThat(entries).hasSize(1)
        assertThat(entries[0].payload).isInstanceOf(TestPayload::class.java).isEqualTo(payload)
        assertThat(entries[0].nextAttemptAt).isEqualTo(now)
    }

    @Test
    fun `should find nothing waiting when the database does not exist yet`() {
        // Given
        whenever(couchDbClient.getDatabaseDocumentsByPrefix(eq(database), eq("OutboxEntry"), eq(JsonNode::class)))
            // NotFoundException is checked, which Mockito's thenThrow rejects for this method
            .thenAnswer { throw NotFoundException(code = TestErrorCode.NOT_FOUND) }

        // Then
        assertThat(outbox.fetchAll()).isEmpty()
    }

    @Test
    fun `should not enqueue what could be delivered right away`() {
        // Given
        whenever(handler.deliver(payload)).thenReturn(OutboxDeliveryResult.Delivered)

        // When
        outbox.deliverNowOrEnqueue("key-1", payload, handler)

        // Then
        verify(couchDbClient, never()).putDatabaseDocument(any(), any(), any())
    }

    @Test
    fun `should enqueue what failed to be delivered right away`() {
        // Given nothing may be dropped, whatever the failure
        stubExisting(eTag = null)
        whenever(handler.deliver(payload)).thenReturn(OutboxDeliveryResult.Rejected("couchdb unreachable"))

        // When
        outbox.deliverNowOrEnqueue("key-1", payload, handler)

        // Then the entry starts with the full retry budget
        assertThat((storedBody() as OutboxEntry<*>).attempts).isEqualTo(0)
    }

    @Test
    fun `should enqueue what threw when delivered right away`() {
        // Given
        stubExisting(eTag = null)
        whenever(handler.deliver(payload)).thenThrow(RuntimeException("boom"))

        // When
        outbox.deliverNowOrEnqueue("key-1", payload, handler)

        // Then
        storedBody()
    }
}
