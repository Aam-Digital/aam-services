package com.aamdigital.aambackendservice.reporting.reportcalculation.core

import com.aamdigital.aambackendservice.common.domain.DomainReference
import com.aamdigital.aambackendservice.common.domain.UseCaseOutcome
import com.aamdigital.aambackendservice.reporting.reportcalculation.ReportCalculation
import com.aamdigital.aambackendservice.reporting.reportcalculation.ReportCalculationStatus
import com.aamdigital.aambackendservice.reporting.reportcalculation.usecase.DefaultReportCalculationUseCase
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.micrometer.observation.ObservationRegistry
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Duration

@ExtendWith(MockitoExtension::class)
class ReportCalculationProcessorTest {
    private lateinit var processor: ReportCalculationProcessor

    @Mock
    lateinit var reportCalculationUseCase: DefaultReportCalculationUseCase

    @Mock
    lateinit var reportCalculationChangeUseCase: ReportCalculationChangeUseCase

    private fun processor(
        attempts: Int = 3,
        // zero so the retry tests do not sleep
        interval: Duration = Duration.ZERO
    ) = ReportCalculationProcessor(
        observationRegistry = ObservationRegistry.create(),
        reportCalculationUseCase = reportCalculationUseCase,
        objectMapper = jacksonObjectMapper(),
        reportCalculationChangeUseCase = reportCalculationChangeUseCase,
        completionRetryAttempts = attempts,
        completionRetryInitialInterval = interval
    )

    private fun succeeds(reportCalculationId: String) {
        whenever(reportCalculationUseCase.run(any()))
            .thenReturn(
                UseCaseOutcome.Success(
                    ReportCalculationData(
                        reportCalculation =
                            ReportCalculation(
                                id = reportCalculationId,
                                report = DomainReference("ReportConfig:1"),
                                status = ReportCalculationStatus.FINISHED_SUCCESS
                            )
                    )
                )
            )
    }

    @BeforeEach
    fun setUp() {
        reset(reportCalculationUseCase, reportCalculationChangeUseCase)
        processor = processor()
    }

    @Test
    fun `should notify webhook subscribers when the calculation finished successfully`() {
        // Given
        val reportCalculationId = "ReportCalculation:1"
        succeeds(reportCalculationId)

        // When
        processor.process(reportCalculationId)

        // Then
        verify(reportCalculationChangeUseCase).handle(eq(reportCalculationId))
    }

    @Test
    fun `should not notify webhook subscribers when the calculation failed`() {
        // Given
        whenever(reportCalculationUseCase.run(any()))
            .thenReturn(
                UseCaseOutcome.Failure(
                    errorCode = ReportCalculationError.UNEXPECTED_ERROR,
                    errorMessage = "boom"
                )
            )

        // When: the failure is already recorded on the calculation document as FINISHED_ERROR,
        // and there is no caller to hand an exception to, so nothing is thrown
        processor.process("ReportCalculation:1")

        // Then
        verify(reportCalculationChangeUseCase, never()).handle(any())
    }

    @Test
    fun `should not let an unexpected failure escape onto the executor thread`() {
        // Given nothing above this call is on a caller's stack, so an escaping exception would only
        // reach the thread's default handler and never be logged
        whenever(reportCalculationUseCase.run(any())).thenThrow(RuntimeException("boom"))

        // When / Then
        processor.process("ReportCalculation:1")
        verify(reportCalculationChangeUseCase, never()).handle(any())
    }

    @Test
    fun `should retry the completion notification and succeed on a later attempt`() {
        // Given
        val reportCalculationId = "ReportCalculation:1"
        succeeds(reportCalculationId)
        whenever(reportCalculationChangeUseCase.handle(eq(reportCalculationId)))
            .thenThrow(RuntimeException("couchdb unreachable"))
            .thenAnswer { }

        // When
        processor.process(reportCalculationId)

        // Then
        verify(reportCalculationChangeUseCase, times(2)).handle(eq(reportCalculationId))
    }

    @Test
    fun `should give up after the configured attempts without failing the completed calculation`() {
        // Given the calculation is already stored, a failure to notify must never reject the
        // message: that would re-run and re-status a calculation that already succeeded.
        val reportCalculationId = "ReportCalculation:1"
        succeeds(reportCalculationId)
        whenever(reportCalculationChangeUseCase.handle(eq(reportCalculationId)))
            .thenThrow(RuntimeException("couchdb unreachable"))

        // When
        processor.process(reportCalculationId)

        // Then
        verify(reportCalculationChangeUseCase, times(3)).handle(eq(reportCalculationId))
    }
}
