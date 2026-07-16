package com.aamdigital.aambackendservice.reporting.reportcalculation.core

import com.aamdigital.aambackendservice.common.couchdb.dto.AttachmentMetaData
import com.aamdigital.aambackendservice.common.domain.DomainReference
import com.aamdigital.aambackendservice.reporting.reportcalculation.ReportCalculation
import com.aamdigital.aambackendservice.reporting.reportcalculation.ReportCalculationStatus
import com.aamdigital.aambackendservice.reporting.reportcalculation.usecase.DefaultReportCalculationChangeUseCase
import com.aamdigital.aambackendservice.reporting.webhook.core.NotificationService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
class DefaultReportCalculationChangeUseCaseTest {
    private lateinit var service: DefaultReportCalculationChangeUseCase

    @Mock
    lateinit var reportCalculationStorage: ReportCalculationStorage

    @Mock
    lateinit var notificationService: NotificationService

    @BeforeEach
    fun setUp() {
        reset(reportCalculationStorage, notificationService)
        service =
            DefaultReportCalculationChangeUseCase(
                reportCalculationStorage = reportCalculationStorage,
                notificationService = notificationService
            )
    }

    /**
     * Build the mocks eagerly (not inside a `whenever(...).thenReturn(...)` argument): stubbing the
     * attachment digest while an outer stubbing is still open would trip Mockito's UnfinishedStubbing.
     */
    private fun reportCalculation(
        id: String,
        status: ReportCalculationStatus = ReportCalculationStatus.FINISHED_SUCCESS,
        digest: String? = null,
        fromAutomaticChangeDetection: Boolean = false
    ): ReportCalculation {
        val attachments =
            digest?.let { digestValue ->
                val attachment = mock<AttachmentMetaData>()
                whenever(attachment.digest).thenReturn(digestValue)
                mutableMapOf("data.json" to attachment)
            } ?: mutableMapOf()

        return ReportCalculation(
            id = id,
            report = DomainReference("Report:1"),
            status = status,
            attachments = attachments,
            fromAutomaticChangeDetection = fromAutomaticChangeDetection
        )
    }

    @Test
    fun `should skip processing if status is not FINISHED_SUCCESS`() {
        // given
        val current = reportCalculation(id = "ReportCalculation:2", status = ReportCalculationStatus.PENDING)
        whenever(reportCalculationStorage.fetchReportCalculation(eq(DomainReference("ReportCalculation:2"))))
            .thenReturn(current)

        // when
        service.handle("ReportCalculation:2")

        // then
        verify(reportCalculationStorage, never()).fetchReportCalculations(any())
        verify(notificationService, never()).sendNotifications(any(), any())
    }

    @Test
    fun `should send notifications if data is changed`() {
        // given
        val current = reportCalculation(id = "ReportCalculation:2", digest = "new-digest")
        val existing = reportCalculation(id = "ReportCalculation:1", digest = "old-digest")
        whenever(reportCalculationStorage.fetchReportCalculation(eq(DomainReference("ReportCalculation:2"))))
            .thenReturn(current)
        whenever(reportCalculationStorage.fetchReportCalculations(any()))
            .thenReturn(listOf(existing))

        // when
        service.handle("ReportCalculation:2")

        // then
        verify(notificationService).sendNotifications(
            eq(DomainReference("Report:1")),
            eq(DomainReference("ReportCalculation:2"))
        )
    }

    @Test
    fun `should delete duplicate automatically created report calculation if it was auto-created from change`() {
        // given
        val current =
            reportCalculation(
                id = "ReportCalculation:2",
                digest = "old-digest",
                fromAutomaticChangeDetection = true
            )
        val existing = reportCalculation(id = "ReportCalculation:1", digest = "old-digest")
        whenever(reportCalculationStorage.fetchReportCalculation(eq(DomainReference("ReportCalculation:2"))))
            .thenReturn(current)
        whenever(reportCalculationStorage.fetchReportCalculations(any()))
            .thenReturn(listOf(existing))

        // when
        service.handle("ReportCalculation:2")

        // then
        verify(reportCalculationStorage).deleteReportCalculation(
            eq(DomainReference("ReportCalculation:2"))
        )
        verify(notificationService, never()).sendNotifications(any(), any())
    }
}
