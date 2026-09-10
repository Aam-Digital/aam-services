package com.aamdigital.aambackendservice.reporting.webhook.storage

import com.aamdigital.aambackendservice.reporting.webhook.WebhookAuthenticationType
import com.aamdigital.aambackendservice.reporting.webhook.WebhookTarget
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

@ExtendWith(MockitoExtension::class)
class WebhookSubscriptionCacheTest {
    @Mock
    lateinit var webhookRepository: WebhookRepository

    /** Clock the test advances by hand, so no test depends on wall-clock timing. */
    private class MutableClock(
        var now: Instant
    ) : Clock() {
        override fun instant(): Instant = now

        override fun getZone(): ZoneOffset = ZoneOffset.UTC

        override fun withZone(zone: java.time.ZoneId): Clock = this
    }

    private fun webhookEntity(
        id: String,
        subscriptions: List<String>
    ) = WebhookEntity(
        id = id,
        label = id,
        target = WebhookTarget(method = "POST", url = "https://example.org"),
        authentication =
            WebhookAuthenticationEntity(
                type = WebhookAuthenticationType.API_KEY,
                data = "data",
                iv = "iv"
            ),
        owner = WebhookOwner(creator = "user"),
        reportSubscriptions = subscriptions.toMutableList(),
        createdAt = Instant.parse("2026-01-01T00:00:00Z")
    )

    @Test
    fun `should collect subscribed report ids from all webhooks`() {
        // Given
        whenever(webhookRepository.fetchAllWebhooks())
            .thenReturn(
                listOf(
                    webhookEntity("Webhook:1", listOf("ReportConfig:a")),
                    webhookEntity("Webhook:2", listOf("ReportConfig:a", "ReportConfig:b"))
                )
            )
        val cache = WebhookSubscriptionCache(webhookRepository, Duration.ofSeconds(1))

        // When
        val result = cache.subscribedReportIds()

        // Then
        assertThat(result).containsExactlyInAnyOrder("ReportConfig:a", "ReportConfig:b")
    }

    @Test
    fun `should serve repeated reads within the ttl from a single fetch`() {
        // Given
        whenever(webhookRepository.fetchAllWebhooks())
            .thenReturn(listOf(webhookEntity("Webhook:1", listOf("ReportConfig:a"))))
        val clock = MutableClock(Instant.parse("2026-01-01T00:00:00Z"))
        val cache = WebhookSubscriptionCache(webhookRepository, Duration.ofSeconds(1), clock)

        // When
        cache.subscribedReportIds()
        clock.now = clock.now.plusMillis(999)
        cache.subscribedReportIds()

        // Then
        verify(webhookRepository, times(1)).fetchAllWebhooks()
    }

    @Test
    fun `should reload once the ttl has elapsed`() {
        // Given
        whenever(webhookRepository.fetchAllWebhooks())
            .thenReturn(listOf(webhookEntity("Webhook:1", listOf("ReportConfig:a"))))
        val clock = MutableClock(Instant.parse("2026-01-01T00:00:00Z"))
        val cache = WebhookSubscriptionCache(webhookRepository, Duration.ofSeconds(1), clock)

        // When
        cache.subscribedReportIds()
        clock.now = clock.now.plusSeconds(1)
        cache.subscribedReportIds()

        // Then
        verify(webhookRepository, times(2)).fetchAllWebhooks()
    }

    @Test
    fun `should reload after being invalidated by a write`() {
        // Given
        whenever(webhookRepository.fetchAllWebhooks())
            .thenReturn(listOf(webhookEntity("Webhook:1", listOf("ReportConfig:a"))))
            .thenReturn(listOf(webhookEntity("Webhook:1", listOf("ReportConfig:a", "ReportConfig:b"))))
        val clock = MutableClock(Instant.parse("2026-01-01T00:00:00Z"))
        val cache = WebhookSubscriptionCache(webhookRepository, Duration.ofSeconds(1), clock)

        // When
        val before = cache.subscribedReportIds()
        cache.invalidate()
        val after = cache.subscribedReportIds()

        // Then
        assertThat(before).containsExactly("ReportConfig:a")
        assertThat(after).containsExactlyInAnyOrder("ReportConfig:a", "ReportConfig:b")
    }

    @Test
    fun `should reload on every read when the ttl is zero`() {
        // Given
        whenever(webhookRepository.fetchAllWebhooks())
            .thenReturn(listOf(webhookEntity("Webhook:1", listOf("ReportConfig:a"))))
        val cache = WebhookSubscriptionCache(webhookRepository, Duration.ZERO)

        // When
        cache.subscribedReportIds()
        cache.subscribedReportIds()

        // Then
        verify(webhookRepository, times(2)).fetchAllWebhooks()
    }
}
