package com.aamdigital.aambackendservice.common.outbox

import com.aamdigital.aambackendservice.common.couchdb.core.CouchDbClient
import com.aamdigital.aambackendservice.common.couchdb.core.CouchDbInitializer
import com.aamdigital.aambackendservice.common.rest.ObjectMapperConfiguration
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

/**
 * Covers the delivery and retry half of [Outbox]: what [Outbox.drain] does with a due entry.
 *
 * Storage and retry are one class, so these drive a real [Outbox] over a mocked [CouchDbClient]
 * rather than mocking the storage half. Entries are therefore stubbed as the JSON CouchDB would
 * return, which also exercises reading them back.
 */
class OutboxDrainTest {
    data class TestPayload(
        val value: String
    )

    private val database = "test-outbox"
    private val maxAttempts = 3
    private val now: Instant = Instant.parse("2026-01-01T00:00:00Z")

    private val couchDbClient = mock<CouchDbClient>()
    private val couchDbInitializer = mock<CouchDbInitializer>()
    private val handler = mock<OutboxHandler<TestPayload>>()
    private val objectMapper = ObjectMapperConfiguration().objectMapper()

    private val retryableSelector = mapOf("attempts" to mapOf("\$lt" to maxAttempts))
    private val parkedSelector = mapOf("attempts" to mapOf("\$gte" to maxAttempts))

    private fun outbox() =
        Outbox(
            database = database,
            payloadType = TestPayload::class,
            handler = handler,
            retryPolicy =
                OutboxRetryPolicy(
                    maxAttempts = maxAttempts,
                    initialInterval = Duration.ofSeconds(10),
                    maxInterval = Duration.ofSeconds(60)
                ),
            couchDbClient = couchDbClient,
            couchDbInitializer = couchDbInitializer,
            objectMapper = objectMapper,
            clock = Clock.fixed(now, ZoneOffset.UTC)
        )

    private fun entry(
        attempts: Int = 0,
        nextAttemptAt: Instant = now
    ) = OutboxEntry(
        id = OutboxEntry.idFor("entry-1"),
        payload = TestPayload("payload-1"),
        attempts = attempts,
        nextAttemptAt = nextAttemptAt,
        createdAt = now
    )

    private fun find(selector: Map<String, Any>) =
        couchDbClient.findDatabaseDocumentsByPrefix(
            eq(database),
            eq("OutboxEntry"),
            eq(selector),
            anyOrNull(),
            eq(JsonNode::class)
        )

    /** Nothing waiting, unless a test says otherwise. */
    private fun stubEmpty() {
        whenever(couchDbClient.findDatabaseDocumentsByPrefix(any(), any(), any(), anyOrNull(), eq(JsonNode::class)))
            .thenReturn(emptyList())
    }

    private fun stubFound(
        selector: Map<String, Any>,
        entries: List<OutboxEntry<TestPayload>>
    ) {
        whenever(find(selector)).thenReturn(entries.map { objectMapper.valueToTree<ObjectNode>(it) })
    }

    private fun storedEntry(): OutboxEntry<*> {
        val captor = argumentCaptor<Any>()
        verify(couchDbClient).putDatabaseDocument(eq(database), any(), captor.capture())
        return captor.firstValue as OutboxEntry<*>
    }

    @Test
    fun `should delete the entry once it has been delivered`() {
        // Given a handler need not be idempotent, so a delivered entry must not be seen again
        stubEmpty()
        val outboxEntry = entry()
        stubFound(retryableSelector, listOf(outboxEntry))
        whenever(handler.deliver(any())).thenReturn(OutboxDeliveryResult.Delivered)

        // When
        outbox().drain()

        // Then
        verify(handler).deliver(eq(outboxEntry.payload))
        verify(couchDbClient).deleteDatabaseDocument(eq(database), eq(outboxEntry.id))
    }

    @Test
    fun `should not attempt an entry whose next attempt is still in the future`() {
        // Given
        stubEmpty()
        stubFound(retryableSelector, listOf(entry(attempts = 1, nextAttemptAt = now.plusSeconds(10))))

        // When
        outbox().drain()

        // Then
        verify(handler, never()).deliver(any())
    }

    @Test
    fun `should back off with a growing interval after a transient failure`() {
        // Given
        stubEmpty()
        stubFound(retryableSelector, listOf(entry(attempts = 1)))
        whenever(handler.deliver(any())).thenReturn(OutboxDeliveryResult.RetryLater("SMTP connection failed"))

        // When
        outbox().drain()

        // Then the second failure waits twice the initial interval
        val stored = storedEntry()
        assertThat(stored.attempts).isEqualTo(2)
        assertThat(stored.nextAttemptAt).isEqualTo(now.plusSeconds(20))
        assertThat(stored.lastError).isEqualTo("SMTP connection failed")
        verify(couchDbClient, never()).deleteDatabaseDocument(any(), any())
    }

    @Test
    fun `should park the entry immediately when the handler rejects it`() {
        // Given a rejection can never succeed, so it must not consume the retry budget
        stubEmpty()
        stubFound(retryableSelector, listOf(entry()))
        whenever(handler.deliver(any()))
            .thenReturn(OutboxDeliveryResult.Rejected("No Handler for this NotificationChannelType"))

        // When
        outbox().drain()

        // Then
        val stored = storedEntry()
        assertThat(stored.attempts).isEqualTo(maxAttempts)
        assertThat(stored.lastError).contains("No Handler")
        verify(couchDbClient, never()).deleteDatabaseDocument(any(), any())
    }

    @Test
    fun `should park the entry when the handler throws`() {
        // Given
        stubEmpty()
        stubFound(retryableSelector, listOf(entry()))
        whenever(handler.deliver(any())).thenThrow(IllegalStateException("boom"))

        // When
        outbox().drain()

        // Then
        val stored = storedEntry()
        assertThat(stored.attempts).isEqualTo(maxAttempts)
        assertThat(stored.lastError).isEqualTo("boom")
    }

    @Test
    fun `should park a delivered entry it could not delete rather than deliver it again next tick`() {
        // Given
        stubEmpty()
        stubFound(retryableSelector, listOf(entry()))
        whenever(handler.deliver(any())).thenReturn(OutboxDeliveryResult.Delivered)
        doThrow(IllegalStateException("couchdb unreachable"))
            .whenever(couchDbClient)
            .deleteDatabaseDocument(any(), any())

        // When
        outbox().drain()

        // Then
        assertThat(storedEntry().attempts).isEqualTo(maxAttempts)
    }

    @Test
    fun `should park the entry once the transient retries are exhausted`() {
        // Given
        stubEmpty()
        stubFound(retryableSelector, listOf(entry(attempts = maxAttempts - 1)))
        whenever(handler.deliver(any())).thenReturn(OutboxDeliveryResult.RetryLater("SMTP connection failed"))

        // When
        outbox().drain()

        // Then
        assertThat(storedEntry().attempts).isEqualTo(maxAttempts)
    }

    @Test
    fun `should retry parked entries once per process so a restart recovers them`() {
        // Given this is the documented recovery path: fix the cause, restart the service, and held
        // entries are retried once - without turning into a hot retry loop.
        stubEmpty()
        stubFound(parkedSelector, listOf(entry(attempts = maxAttempts)))
        val outbox = outbox()

        // When
        outbox.drain()
        outbox.drain()

        // Then
        verify(couchDbClient, times(1)).findDatabaseDocumentsByPrefix(
            eq(database),
            eq("OutboxEntry"),
            eq(parkedSelector),
            anyOrNull(),
            eq(JsonNode::class)
        )
        val stored = storedEntry()
        assertThat(stored.attempts).isEqualTo(0)
        assertThat(stored.nextAttemptAt).isEqualTo(now)
    }

    @Test
    fun `should still un-park after a restart when the parked entries could not be read at first`() {
        // Given CouchDB is unreachable on the first tick after the restart
        stubEmpty()
        whenever(find(parkedSelector))
            .thenThrow(IllegalStateException("couchdb unreachable"))
            .thenReturn(listOf(objectMapper.valueToTree<ObjectNode>(entry(attempts = maxAttempts))))
        val outbox = outbox()

        // When the first tick fails (the job's backoff handles it) and a later one succeeds
        assertThatThrownBy { outbox.drain() }.hasMessage("couchdb unreachable")
        outbox.drain()

        // Then
        assertThat(storedEntry().attempts).isEqualTo(0)
    }

    @Test
    fun `should never read parked entries on a regular tick`() {
        // Given parked entries pile up while their cause lasts, and the drain runs every few seconds
        stubEmpty()
        val outbox = outbox()

        // When
        outbox.drain()
        outbox.drain()
        outbox.drain()

        // Then
        verify(couchDbClient, times(1)).findDatabaseDocumentsByPrefix(
            eq(database),
            eq("OutboxEntry"),
            eq(parkedSelector),
            anyOrNull(),
            eq(JsonNode::class)
        )
        verify(couchDbClient, times(3)).findDatabaseDocumentsByPrefix(
            eq(database),
            eq("OutboxEntry"),
            eq(retryableSelector),
            anyOrNull(),
            eq(JsonNode::class)
        )
    }

    @Test
    fun `should do nothing when the outbox is empty`() {
        // Given
        stubEmpty()

        // When
        outbox().drain()

        // Then
        verify(handler, never()).deliver(any())
        verify(couchDbClient, never()).putDatabaseDocument(any(), any(), any())
    }

    @Test
    fun `should cap the retry delay at the maximum interval`() {
        // Given
        val policy =
            OutboxRetryPolicy(
                maxAttempts = 10,
                initialInterval = Duration.ofSeconds(10),
                maxInterval = Duration.ofSeconds(60)
            )

        // Then
        assertThat(policy.delayAfter(1)).isEqualTo(Duration.ofSeconds(10))
        assertThat(policy.delayAfter(3)).isEqualTo(Duration.ofSeconds(40))
        assertThat(policy.delayAfter(4)).isEqualTo(Duration.ofSeconds(60))
    }
}
