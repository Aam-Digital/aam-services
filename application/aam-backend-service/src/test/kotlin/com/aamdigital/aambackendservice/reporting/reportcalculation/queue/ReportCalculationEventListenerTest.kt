package com.aamdigital.aambackendservice.reporting.reportcalculation.queue

import com.aamdigital.aambackendservice.common.domain.DomainReference
import com.aamdigital.aambackendservice.common.domain.UseCaseOutcome
import com.aamdigital.aambackendservice.reporting.reportcalculation.ReportCalculation
import com.aamdigital.aambackendservice.reporting.reportcalculation.ReportCalculationCompletedEvent
import com.aamdigital.aambackendservice.reporting.reportcalculation.ReportCalculationEvent
import com.aamdigital.aambackendservice.reporting.reportcalculation.ReportCalculationStatus
import com.aamdigital.aambackendservice.reporting.reportcalculation.core.ReportCalculationData
import com.aamdigital.aambackendservice.reporting.reportcalculation.core.ReportCalculationError
import com.aamdigital.aambackendservice.reporting.reportcalculation.di.ReportCalculationQueueConfiguration.Companion.REPORT_CALCULATION_COMPLETED_QUEUE
import com.aamdigital.aambackendservice.reporting.reportcalculation.usecase.DefaultReportCalculationUseCase
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.micrometer.observation.ObservationRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.amqp.AmqpRejectAndDontRequeueException

@ExtendWith(MockitoExtension::class)
class ReportCalculationEventListenerTest {
    private lateinit var listener: ReportCalculationEventListener

    @Mock
    lateinit var reportCalculationUseCase: DefaultReportCalculationUseCase

    @Mock
    lateinit var reportCalculationEventPublisher: RabbitMqReportCalculationEventPublisher

    @BeforeEach
    fun setUp() {
        reset(reportCalculationUseCase, reportCalculationEventPublisher)
        listener =
            ReportCalculationEventListener(
                observationRegistry = ObservationRegistry.create(),
                reportCalculationUseCase = reportCalculationUseCase,
                objectMapper = jacksonObjectMapper(),
                reportCalculationEventPublisher = reportCalculationEventPublisher,
            )
    }

    @Test
    fun `publishes a completion event when the calculation finished successfully`() {
        // given
        val reportCalculationId = "ReportCalculation:1"
        whenever(reportCalculationUseCase.run(any()))
            .thenReturn(
                UseCaseOutcome.Success(
                    ReportCalculationData(
                        reportCalculation =
                            ReportCalculation(
                                id = reportCalculationId,
                                report = DomainReference("ReportConfig:1"),
                                status = ReportCalculationStatus.FINISHED_SUCCESS,
                            ),
                    ),
                ),
            )

        // when
        listener.handleReportCalculationEvent(ReportCalculationEvent(reportCalculationId))

        // then: the completion is announced via its own queue, decoupled from the CouchDB changes feed,
        // so the webhook-notification path is triggered by an explicit event instead of an observed DB write.
        val eventCaptor = argumentCaptor<ReportCalculationCompletedEvent>()
        verify(reportCalculationEventPublisher).publish(
            eq(REPORT_CALCULATION_COMPLETED_QUEUE),
            eventCaptor.capture(),
        )
        assertEquals(reportCalculationId, eventCaptor.firstValue.reportCalculationId)
    }

    @Test
    fun `does not publish a completion event when the calculation failed`() {
        // given
        whenever(reportCalculationUseCase.run(any()))
            .thenReturn(
                UseCaseOutcome.Failure(
                    errorCode = ReportCalculationError.UNEXPECTED_ERROR,
                    errorMessage = "boom",
                ),
            )

        // when / then
        assertThrows<AmqpRejectAndDontRequeueException> {
            listener.handleReportCalculationEvent(ReportCalculationEvent("ReportCalculation:1"))
        }
        verify(reportCalculationEventPublisher, never()).publish(any(), any())
    }
}
