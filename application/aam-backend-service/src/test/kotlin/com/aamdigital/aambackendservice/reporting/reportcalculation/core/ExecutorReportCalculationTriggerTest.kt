package com.aamdigital.aambackendservice.reporting.reportcalculation.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException

@ExtendWith(MockitoExtension::class)
class ExecutorReportCalculationTriggerTest {
    @Mock
    lateinit var reportCalculationProcessor: ReportCalculationProcessor

    @Test
    fun `should run the calculation on the executor`() {
        // Given
        val trigger =
            ExecutorReportCalculationTrigger(
                reportCalculationExecutor = Executor { it.run() },
                reportCalculationProcessor = reportCalculationProcessor
            )

        // When
        trigger.trigger("ReportCalculation:1")

        // Then
        verify(reportCalculationProcessor).process(eq("ReportCalculation:1"))
    }

    @Test
    fun `should report a queued calculation as in flight and forget it once finished`() {
        // Given the sweeper tells a stuck calculation from a queued one by asking this
        val submitted = mutableListOf<Runnable>()
        val trigger =
            ExecutorReportCalculationTrigger(
                reportCalculationExecutor = Executor { submitted.add(it) },
                reportCalculationProcessor = reportCalculationProcessor
            )

        // When
        trigger.trigger("ReportCalculation:1")

        // Then
        assertThat(trigger.inFlight()).containsExactly("ReportCalculation:1")

        // When the queued work finally runs
        submitted.single().run()

        // Then
        assertThat(trigger.inFlight()).isEmpty()
    }

    @Test
    fun `should forget an in-flight calculation even when processing throws`() {
        // Given
        whenever(reportCalculationProcessor.process(any())).thenThrow(RuntimeException("boom"))
        val trigger =
            ExecutorReportCalculationTrigger(
                reportCalculationExecutor = Executor { it.run() },
                reportCalculationProcessor = reportCalculationProcessor
            )

        // When / Then: the sweeper would otherwise never reconsider it
        runCatching { trigger.trigger("ReportCalculation:1") }
        assertThat(trigger.inFlight()).isEmpty()
    }

    @Test
    fun `should not report a rejected calculation as in flight`() {
        // Given a rejected calculation stays PENDING and must be visible to the sweeper
        val trigger =
            ExecutorReportCalculationTrigger(
                reportCalculationExecutor = Executor { throw RejectedExecutionException("saturated") },
                reportCalculationProcessor = reportCalculationProcessor
            )

        // When
        trigger.trigger("ReportCalculation:1")

        // Then
        assertThat(trigger.inFlight()).isEmpty()
    }

    @Test
    fun `should not propagate a rejected submission so the calculation stays pending`() {
        // Given the backlog is bounded on purpose; a rejected calculation is left PENDING for
        // ReportCalculationSweeper to pick up rather than failing the caller
        val trigger =
            ExecutorReportCalculationTrigger(
                reportCalculationExecutor = Executor { throw RejectedExecutionException("saturated") },
                reportCalculationProcessor = reportCalculationProcessor
            )

        // When
        trigger.trigger("ReportCalculation:1")

        // Then
        verify(reportCalculationProcessor, never()).process(any())
    }
}
