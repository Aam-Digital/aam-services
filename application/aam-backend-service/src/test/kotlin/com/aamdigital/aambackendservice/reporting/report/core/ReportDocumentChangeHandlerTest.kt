package com.aamdigital.aambackendservice.reporting.report.core

import com.aamdigital.aambackendservice.common.changes.DocumentChangeEvent
import com.aamdigital.aambackendservice.common.domain.DomainReference
import com.aamdigital.aambackendservice.reporting.reportcalculation.core.CreateReportCalculationRequest
import com.aamdigital.aambackendservice.reporting.reportcalculation.core.ReportCalculationDebouncer
import com.aamdigital.aambackendservice.reporting.webhook.storage.WebhookSubscriptionCache
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
class ReportDocumentChangeHandlerTest {
    @Mock
    lateinit var reportCalculationDebouncer: ReportCalculationDebouncer

    @Mock
    lateinit var identifyAffectedReportsUseCase: IdentifyAffectedReportsUseCase

    @Mock
    lateinit var webhookSubscriptionCache: WebhookSubscriptionCache

    private val handler by lazy {
        ReportDocumentChangeHandler(
            reportCalculationDebouncer = reportCalculationDebouncer,
            identifyAffectedReportsUseCase = identifyAffectedReportsUseCase,
            webhookSubscriptionCache = webhookSubscriptionCache
        )
    }

    private val changeEvent =
        DocumentChangeEvent(
            database = "app",
            documentId = "Child:1",
            rev = "1-abc",
            currentVersion = emptyMap<String, Any>(),
            previousVersion = emptyMap<String, Any>(),
            deleted = false
        )

    @Test
    fun `should record a debounced trigger only for webhook-subscribed affected reports`() {
        // Given
        whenever(identifyAffectedReportsUseCase.analyse(changeEvent))
            .thenReturn(
                listOf(
                    DomainReference("ReportConfig:subscribed"),
                    DomainReference("ReportConfig:unsubscribed")
                )
            )
        whenever(webhookSubscriptionCache.subscribedReportIds())
            .thenReturn(setOf("ReportConfig:subscribed"))

        // When
        handler.handle(changeEvent)

        // Then
        val captor = argumentCaptor<CreateReportCalculationRequest>()
        verify(reportCalculationDebouncer, times(1)).recordChange(captor.capture())
        assertThat(captor.firstValue.report.id).isEqualTo("ReportConfig:subscribed")
        assertThat(captor.firstValue.fromAutomaticChangeDetection).isTrue()
    }

    @Test
    fun `should not look up webhook subscriptions when no report is affected`() {
        // Given the handler runs for every changed document, so it must skip work it cannot need
        whenever(identifyAffectedReportsUseCase.analyse(changeEvent)).thenReturn(emptyList())

        // When
        handler.handle(changeEvent)

        // Then
        verify(webhookSubscriptionCache, never()).subscribedReportIds()
        verify(reportCalculationDebouncer, never()).recordChange(any())
    }
}
