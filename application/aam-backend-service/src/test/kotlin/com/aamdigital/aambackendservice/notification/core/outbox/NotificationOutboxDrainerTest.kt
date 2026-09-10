package com.aamdigital.aambackendservice.notification.core.outbox

import com.aamdigital.aambackendservice.common.domain.UseCaseOutcome
import com.aamdigital.aambackendservice.common.error.AamErrorCode
import com.aamdigital.aambackendservice.notification.core.create.CreateNotificationData
import com.aamdigital.aambackendservice.notification.core.create.CreateNotificationUseCase
import com.aamdigital.aambackendservice.notification.core.create.TransientNotificationException
import com.aamdigital.aambackendservice.notification.domain.NotificationChannelType
import com.aamdigital.aambackendservice.notification.domain.NotificationDetails
import com.aamdigital.aambackendservice.notification.domain.NotificationType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.net.ConnectException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

@ExtendWith(MockitoExtension::class)
class NotificationOutboxDrainerTest {
    private enum class TestErrorCode : AamErrorCode { INVALID_NOTIFICATION_CHANNEL_TYPE }

    @Mock
    lateinit var notificationOutboxRepository: NotificationOutboxRepository

    @Mock
    lateinit var createNotificationUseCase: CreateNotificationUseCase

    private val now: Instant = Instant.parse("2026-01-01T00:00:00Z")
    private val clock: Clock =
        object : Clock() {
            override fun instant(): Instant = now

            override fun getZone(): ZoneOffset = ZoneOffset.UTC

            override fun withZone(zone: ZoneId): Clock = this
        }

    private val maxAttempts = 3

    private fun drainer() =
        NotificationOutboxDrainer(
            notificationOutboxRepository = notificationOutboxRepository,
            createNotificationUseCase = createNotificationUseCase,
            maxAttempts = maxAttempts,
            initialRetryInterval = Duration.ofSeconds(10),
            maxRetryInterval = Duration.ofSeconds(60),
            clock = clock
        )

    private fun entry(
        channel: NotificationChannelType = NotificationChannelType.EMAIL,
        attempts: Int = 0,
        nextAttemptAt: Instant = now
    ) = NotificationOutboxEntry(
        id = NotificationOutboxEntry.idFor("notification-1", channel),
        userIdentifier = "user-1",
        notificationChannelType = channel,
        notificationRule = "ext-1",
        details =
            NotificationDetails(
                notificationType = NotificationType.ENTITY_CHANGE,
                title = "Rule 1"
            ),
        attempts = attempts,
        nextAttemptAt = nextAttemptAt,
        createdAt = now
    )

    private fun succeeds() {
        whenever(createNotificationUseCase.run(any()))
            .thenReturn(
                UseCaseOutcome.Success(
                    CreateNotificationData(success = true, messageCreated = true, messageReference = null)
                )
            )
    }

    @Test
    fun `should delete the entry once it has been delivered`() {
        // Given push and email are not idempotent, so a delivered entry must not be seen again
        val outboxEntry = entry()
        whenever(notificationOutboxRepository.fetchPending()).thenReturn(listOf(outboxEntry))
        succeeds()

        // When
        drainer().drain()

        // Then
        verify(notificationOutboxRepository).delete(eq(outboxEntry.id))
    }

    @Test
    fun `should not attempt an entry whose next attempt is still in the future`() {
        // Given
        whenever(notificationOutboxRepository.fetchPending())
            .thenReturn(listOf(entry(attempts = 1, nextAttemptAt = now.plusSeconds(10))))

        // When
        drainer().drain()

        // Then
        verify(createNotificationUseCase, never()).run(any())
    }

    @Test
    fun `should back off with a growing interval after a transient failure`() {
        // Given
        whenever(notificationOutboxRepository.fetchPending()).thenReturn(listOf(entry()))
        whenever(createNotificationUseCase.run(any()))
            .thenThrow(
                TransientNotificationException("SMTP connection failed", ConnectException("refused"))
            )

        // When
        drainer().drain()

        // Then
        val captor = argumentCaptor<NotificationOutboxEntry>()
        verify(notificationOutboxRepository).store(captor.capture())
        assertThat(captor.firstValue.attempts).isEqualTo(1)
        assertThat(captor.firstValue.nextAttemptAt).isEqualTo(now.plusSeconds(10))
        assertThat(captor.firstValue.lastError).contains("SMTP connection failed")
        verify(notificationOutboxRepository, never()).delete(any())
    }

    @Test
    fun `should park the entry immediately on a permanent failure`() {
        // Given a permanent failure can never succeed, so it must not consume the retry budget
        whenever(notificationOutboxRepository.fetchPending()).thenReturn(listOf(entry()))
        whenever(createNotificationUseCase.run(any()))
            .thenReturn(
                UseCaseOutcome.Failure(
                    errorCode = TestErrorCode.INVALID_NOTIFICATION_CHANNEL_TYPE,
                    errorMessage = "No Handler for this NotificationChannelType"
                )
            )

        // When
        drainer().drain()

        // Then
        val captor = argumentCaptor<NotificationOutboxEntry>()
        verify(notificationOutboxRepository).store(captor.capture())
        assertThat(captor.firstValue.attempts).isEqualTo(maxAttempts)
        assertThat(captor.firstValue.lastError).contains("No Handler")
        verify(notificationOutboxRepository, never()).delete(any())
    }

    @Test
    fun `should park the entry once the transient retries are exhausted`() {
        // Given
        whenever(notificationOutboxRepository.fetchPending())
            .thenReturn(listOf(entry(attempts = maxAttempts - 1)))
        whenever(createNotificationUseCase.run(any()))
            .thenThrow(
                TransientNotificationException("SMTP connection failed", ConnectException("refused"))
            )

        // When
        drainer().drain()

        // Then
        val captor = argumentCaptor<NotificationOutboxEntry>()
        verify(notificationOutboxRepository).store(captor.capture())
        assertThat(captor.firstValue.attempts).isEqualTo(maxAttempts)
    }

    @Test
    fun `should retry parked entries once per process so a restart recovers them`() {
        // Given this reproduces the documented recovery path: fix the cause, restart the service,
        // and held notifications are retried once - without turning into a hot retry loop.
        val parked = entry(attempts = maxAttempts)
        whenever(notificationOutboxRepository.fetchPending()).thenReturn(listOf(parked))
        val drainer = drainer()

        // When
        drainer.drain()
        drainer.drain()

        // Then
        val captor = argumentCaptor<NotificationOutboxEntry>()
        verify(notificationOutboxRepository, times(1)).store(captor.capture())
        assertThat(captor.firstValue.attempts).isEqualTo(0)
        assertThat(captor.firstValue.nextAttemptAt).isEqualTo(now)
        // the un-parked entry is only attempted on a later tick, once it has been re-read
        verify(createNotificationUseCase, never()).run(any())
    }

    @Test
    fun `should do nothing when the outbox is empty`() {
        // Given
        whenever(notificationOutboxRepository.fetchPending()).thenReturn(emptyList())

        // When
        drainer().drain()

        // Then
        verify(createNotificationUseCase, never()).run(any())
        verify(notificationOutboxRepository, never()).store(any())
    }
}
