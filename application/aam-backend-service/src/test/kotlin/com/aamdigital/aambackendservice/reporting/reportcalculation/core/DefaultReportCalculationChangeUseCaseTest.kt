package com.aamdigital.aambackendservice.reporting.reportcalculation.core

import com.aamdigital.aambackendservice.common.changes.domain.DocumentChangeEvent
import com.aamdigital.aambackendservice.common.couchdb.dto.AttachmentMetaData
import com.aamdigital.aambackendservice.common.domain.DomainReference
import com.aamdigital.aambackendservice.reporting.reportcalculation.ReportCalculation
import com.aamdigital.aambackendservice.reporting.reportcalculation.ReportCalculationStatus
import com.aamdigital.aambackendservice.reporting.reportcalculation.storage.ReportCalculationEntity
import com.aamdigital.aambackendservice.reporting.reportcalculation.usecase.DefaultReportCalculationChangeUseCase
import com.aamdigital.aambackendservice.reporting.webhook.core.NotificationService
import com.fasterxml.jackson.databind.ObjectMapper
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
    lateinit var objectMapper: ObjectMapper

    @Mock
    lateinit var notificationService: NotificationService

    @BeforeEach
    fun setUp() {
        reset(reportCalculationStorage, objectMapper, notificationService)
        service = DefaultReportCalculationChangeUseCase(
            reportCalculationStorage = reportCalculationStorage,
            objectMapper = objectMapper,
            notificationService = notificationService
        )
    }

    private fun generateDocumentChangeEvent(
        documentId: String = "ReportCalculation:1"
    ): DocumentChangeEvent {
        return DocumentChangeEvent(
            database = "report-calculation",
            documentId = documentId,
            rev = "1-abc",
            currentVersion = mapOf("_id" to documentId),
            previousVersion = mapOf("_id" to documentId),
            deleted = false,
        )
    }

    @Test
    fun `should skip processing if status is not FINISHED_SUCCESS`() {
        // given
        val reportCalculationEntity = mock<ReportCalculationEntity> {
            on { status } doReturn ReportCalculationStatus.PENDING
        }

        val documentChangeEvent = generateDocumentChangeEvent()
        whenever(objectMapper.convertValue(documentChangeEvent.currentVersion, ReportCalculationEntity::class.java))
            .thenReturn(reportCalculationEntity)

        // when
        service.handle(documentChangeEvent)

        // then
        verify(reportCalculationStorage, never()).fetchReportCalculations(any())
        verify(notificationService, never()).sendNotifications(any(), any())
    }

    @Test
    fun `should send notifications if data is changed`() {
        // given
        val currentReportCalculation = ReportCalculationEntity(
            id = "ReportCalculation:2",
            report = DomainReference("Report:1"),
            status = ReportCalculationStatus.FINISHED_SUCCESS,
            attachments = mutableMapOf("data.json" to mock<AttachmentMetaData> {
                on { digest } doReturn "new-digest"
            })
        )
        val documentChangeEvent = generateDocumentChangeEvent(currentReportCalculation.id)
        whenever(objectMapper.convertValue(documentChangeEvent.currentVersion, ReportCalculationEntity::class.java))
            .thenReturn(currentReportCalculation)

        val existingReportCalculation = ReportCalculation(
            id = "ReportCalculation:1",
            report = DomainReference("Report:1"),
            status = ReportCalculationStatus.FINISHED_SUCCESS,
            attachments = mutableMapOf("data.json" to mock<AttachmentMetaData> {
                on { digest } doReturn "old-digest"
            })
        )
        whenever(reportCalculationStorage.fetchReportCalculations(any()))
            .thenReturn(listOf(existingReportCalculation))

        // when
        service.handle(documentChangeEvent)

        // then
        verify(notificationService).sendNotifications(
            eq(DomainReference("Report:1")),
            eq(DomainReference(currentReportCalculation.id))
        )
    }

    @Test
    fun `should delete duplicate automatically created report calculation if it was auto-created from change`() {
        // given
        val currentReportCalculation = ReportCalculationEntity(
            id = "ReportCalculation:2",
            report = DomainReference("Report:1"),
            status = ReportCalculationStatus.FINISHED_SUCCESS,
            attachments = mutableMapOf("data.json" to mock<AttachmentMetaData> {
                on { digest } doReturn "old-digest"
            }),
            fromAutomaticChangeDetection = true,
        )
        val documentChangeEvent = generateDocumentChangeEvent(currentReportCalculation.id)
        whenever(objectMapper.convertValue(documentChangeEvent.currentVersion, ReportCalculationEntity::class.java))
            .thenReturn(currentReportCalculation)

        val existingReportCalculation = ReportCalculation(
            id = "ReportCalculation:1",
            report = DomainReference("Report:1"),
            status = ReportCalculationStatus.FINISHED_SUCCESS,
            attachments = mutableMapOf("data.json" to mock<AttachmentMetaData> {
                on { digest } doReturn "old-digest"
            })
        )
        whenever(reportCalculationStorage.fetchReportCalculations(any()))
            .thenReturn(listOf(existingReportCalculation))

        // when
        service.handle(documentChangeEvent)

        // then
        verify(reportCalculationStorage).deleteReportCalculation(
            eq(DomainReference(currentReportCalculation.id))
        )
        verify(notificationService, never()).sendNotifications(any(), any())
    }
}
