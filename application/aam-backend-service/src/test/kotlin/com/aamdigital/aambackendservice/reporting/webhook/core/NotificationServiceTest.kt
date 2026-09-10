package com.aamdigital.aambackendservice.reporting.webhook.core

import com.aamdigital.aambackendservice.common.domain.DomainReference
import com.aamdigital.aambackendservice.reporting.webhook.Webhook
import com.aamdigital.aambackendservice.reporting.webhook.WebhookAuthentication
import com.aamdigital.aambackendservice.reporting.webhook.WebhookAuthenticationType
import com.aamdigital.aambackendservice.reporting.webhook.WebhookEvent
import com.aamdigital.aambackendservice.reporting.webhook.WebhookTarget
import com.aamdigital.aambackendservice.reporting.webhook.storage.WebhookOwner
import com.aamdigital.aambackendservice.reporting.webhook.storage.WebhookStorage
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException

@ExtendWith(MockitoExtension::class)
class NotificationServiceTest {
    @Mock
    lateinit var webhookStorage: WebhookStorage

    @Mock
    lateinit var triggerWebhookUseCase: TriggerWebhookUseCase

    /** Runs submitted work inline, so the test can assert without waiting on a pool. */
    private val directExecutor = Executor { it.run() }

    private fun webhook(
        id: String,
        subscriptions: List<String>
    ) = Webhook(
        id = id,
        label = id,
        target = WebhookTarget(method = "POST", url = "https://example.org"),
        authentication =
            WebhookAuthentication(
                type = WebhookAuthenticationType.API_KEY,
                secret = "secret"
            ),
        owner = WebhookOwner(creator = "user"),
        reportSubscriptions = subscriptions.map { DomainReference(it) }.toMutableList()
    )

    @Test
    fun `should trigger only the webhooks subscribed to the report`() {
        // Given
        whenever(webhookStorage.fetchAllWebhooks())
            .thenReturn(
                listOf(
                    webhook("Webhook:subscribed", listOf("ReportConfig:1")),
                    webhook("Webhook:other", listOf("ReportConfig:2"))
                )
            )
        val service = NotificationService(webhookStorage, triggerWebhookUseCase, directExecutor)

        // When
        service.sendNotifications(DomainReference("ReportConfig:1"), DomainReference("ReportCalculation:1"))

        // Then
        val captor = argumentCaptor<WebhookEvent>()
        verify(triggerWebhookUseCase).trigger(captor.capture())
        assertThat(captor.firstValue.webhookId).isEqualTo("Webhook:subscribed")
        assertThat(captor.firstValue.reportId).isEqualTo("ReportConfig:1")
        assertThat(captor.firstValue.calculationId).isEqualTo("ReportCalculation:1")
    }

    @Test
    fun `should not propagate a failing webhook callback to the caller`() {
        // Given delivery is fire-and-forget: a failing subscriber must not fail the report
        // calculation that produced the result, nor the request that registered the subscription.
        whenever(triggerWebhookUseCase.trigger(any()))
            .thenThrow(RuntimeException("subscriber unreachable"))
        val service = NotificationService(webhookStorage, triggerWebhookUseCase, directExecutor)

        // When / Then
        service.triggerWebhook(
            report = DomainReference("ReportConfig:1"),
            reportCalculation = DomainReference("ReportCalculation:1"),
            webhook = DomainReference("Webhook:1")
        )
    }

    @Test
    fun `should not propagate a rejected submission when the executor is saturated`() {
        // Given the backlog is bounded on purpose, so a saturated executor drops the callback
        // rather than blocking the caller.
        val rejectingExecutor = Executor { throw RejectedExecutionException("saturated") }
        val service = NotificationService(webhookStorage, triggerWebhookUseCase, rejectingExecutor)

        // When
        service.triggerWebhook(
            report = DomainReference("ReportConfig:1"),
            reportCalculation = DomainReference("ReportCalculation:1"),
            webhook = DomainReference("Webhook:1")
        )

        // Then
        verify(triggerWebhookUseCase, never()).trigger(any())
    }
}
