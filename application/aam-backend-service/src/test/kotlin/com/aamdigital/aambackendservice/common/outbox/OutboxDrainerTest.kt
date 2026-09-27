package com.aamdigital.aambackendservice.common.outbox

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

@ExtendWith(MockitoExtension::class)
class OutboxDrainerTest {
    data class TestPayload(
        val value: String
    )

    @Mock
    lateinit var outbox: Outbox<TestPayload>

    @Mock
    lateinit var handler: OutboxHandler<TestPayload>

    private val now: Instant = Instant.parse("2026-01-01T00:00:00Z")
    private val clock: Clock =
        object : Clock() {
            override fun instant(): Instant = now

            override fun getZone(): ZoneOffset = ZoneOffset.UTC

            override fun withZone(zone: ZoneId): Clock = this
        }

    private val maxAttempts = 3

    private fun drainer() =
        OutboxDrainer(
            outbox = outbox,
            handler = handler,
            retryPolicy =
                OutboxRetryPolicy(
                    maxAttempts = maxAttempts,
                    initialInterval = Duration.ofSeconds(10),
                    maxInterval = Duration.ofSeconds(60)
                ),
            clock = clock
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

    private fun storedEntry(): OutboxEntry<TestPayload> {
        val captor = argumentCaptor<OutboxEntry<TestPayload>>()
        verify(outbox).store(captor.capture())
        return captor.firstValue
    }

    @Test
    fun `should delete the entry once it has been delivered`() {
        // Given a handler need not be idempotent, so a delivered entry must not be seen again
        val outboxEntry = entry()
        whenever(outbox.fetchAll()).thenReturn(listOf(outboxEntry))
        whenever(handler.deliver(any())).thenReturn(OutboxDeliveryResult.Delivered)

        // When
        drainer().drain()

        // Then
        verify(handler).deliver(eq(outboxEntry.payload))
        verify(outbox).delete(eq(outboxEntry.id))
    }

    @Test
    fun `should not attempt an entry whose next attempt is still in the future`() {
        // Given
        whenever(outbox.fetchAll()).thenReturn(listOf(entry(attempts = 1, nextAttemptAt = now.plusSeconds(10))))

        // When
        drainer().drain()

        // Then
        verify(handler, never()).deliver(any())
    }

    @Test
    fun `should back off with a growing interval after a transient failure`() {
        // Given
        whenever(outbox.fetchAll()).thenReturn(listOf(entry(attempts = 1)))
        whenever(handler.deliver(any())).thenReturn(OutboxDeliveryResult.RetryLater("SMTP connection failed"))

        // When
        drainer().drain()

        // Then the second failure waits twice the initial interval
        val stored = storedEntry()
        assertThat(stored.attempts).isEqualTo(2)
        assertThat(stored.nextAttemptAt).isEqualTo(now.plusSeconds(20))
        assertThat(stored.lastError).isEqualTo("SMTP connection failed")
        verify(outbox, never()).delete(any())
    }

    @Test
    fun `should park the entry immediately when the handler rejects it`() {
        // Given a rejection can never succeed, so it must not consume the retry budget
        whenever(outbox.fetchAll()).thenReturn(listOf(entry()))
        whenever(handler.deliver(any()))
            .thenReturn(OutboxDeliveryResult.Rejected("No Handler for this NotificationChannelType"))

        // When
        drainer().drain()

        // Then
        val stored = storedEntry()
        assertThat(stored.attempts).isEqualTo(maxAttempts)
        assertThat(stored.lastError).contains("No Handler")
        verify(outbox, never()).delete(any())
    }

    @Test
    fun `should park the entry when the handler throws`() {
        // Given
        whenever(outbox.fetchAll()).thenReturn(listOf(entry()))
        whenever(handler.deliver(any())).thenThrow(IllegalStateException("boom"))

        // When
        drainer().drain()

        // Then
        val stored = storedEntry()
        assertThat(stored.attempts).isEqualTo(maxAttempts)
        assertThat(stored.lastError).isEqualTo("boom")
    }

    @Test
    fun `should park a delivered entry it could not delete rather than deliver it again next tick`() {
        // Given
        val outboxEntry = entry()
        whenever(outbox.fetchAll()).thenReturn(listOf(outboxEntry))
        whenever(handler.deliver(any())).thenReturn(OutboxDeliveryResult.Delivered)
        doThrow(IllegalStateException("couchdb unreachable")).whenever(outbox).delete(any())

        // When
        drainer().drain()

        // Then
        assertThat(storedEntry().attempts).isEqualTo(maxAttempts)
    }

    @Test
    fun `should park the entry once the transient retries are exhausted`() {
        // Given
        whenever(outbox.fetchAll()).thenReturn(listOf(entry(attempts = maxAttempts - 1)))
        whenever(handler.deliver(any())).thenReturn(OutboxDeliveryResult.RetryLater("SMTP connection failed"))

        // When
        drainer().drain()

        // Then
        assertThat(storedEntry().attempts).isEqualTo(maxAttempts)
    }

    @Test
    fun `should retry parked entries once per process so a restart recovers them`() {
        // Given this is the documented recovery path: fix the cause, restart the service, and held
        // entries are retried once - without turning into a hot retry loop.
        whenever(outbox.fetchAll()).thenReturn(listOf(entry(attempts = maxAttempts)))
        val drainer = drainer()

        // When
        drainer.drain()
        drainer.drain()

        // Then
        val captor = argumentCaptor<OutboxEntry<TestPayload>>()
        verify(outbox, times(1)).store(captor.capture())
        assertThat(captor.firstValue.attempts).isEqualTo(0)
        assertThat(captor.firstValue.nextAttemptAt).isEqualTo(now)
        // the un-parked entry is only attempted on a later tick, once it has been re-read
        verify(handler, never()).deliver(any())
    }

    @Test
    fun `should do nothing when the outbox is empty`() {
        // Given
        whenever(outbox.fetchAll()).thenReturn(emptyList())

        // When
        drainer().drain()

        // Then
        verify(handler, never()).deliver(any())
        verify(outbox, never()).store(any())
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
