package com.aamdigital.aambackendservice.reporting.reportcalculation.core

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
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
