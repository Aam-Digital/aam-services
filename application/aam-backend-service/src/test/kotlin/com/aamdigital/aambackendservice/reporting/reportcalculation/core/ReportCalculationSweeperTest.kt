package com.aamdigital.aambackendservice.reporting.reportcalculation.core

import com.aamdigital.aambackendservice.common.domain.DomainReference
import com.aamdigital.aambackendservice.reporting.reportcalculation.ReportCalculation
import com.aamdigital.aambackendservice.reporting.reportcalculation.ReportCalculationStatus
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
class ReportCalculationSweeperTest {
    @Mock
    lateinit var reportCalculationStorage: ReportCalculationStorage

    @Mock
    lateinit var reportCalculationTrigger: ReportCalculationTrigger

    private val sweeper by lazy {
        ReportCalculationSweeper(
            reportCalculationStorage = reportCalculationStorage,
            reportCalculationTrigger = reportCalculationTrigger
        )
    }

    private fun calculation(
        id: String,
        status: ReportCalculationStatus
    ) = ReportCalculation(
        id = id,
        report = DomainReference("ReportConfig:1"),
        status = status
    )

    private fun stored(vararg calculations: ReportCalculation) {
        whenever(reportCalculationStorage.fetchAllReportCalculations()).thenReturn(calculations.toList())
    }

    @Test
    fun `should re-trigger a pending calculation that nothing is running`() {
        // Given a crash between storing the document and submitting it leaves a PENDING
        // calculation the executor knows nothing about
        stored(calculation("ReportCalculation:orphaned", ReportCalculationStatus.PENDING))
        whenever(reportCalculationTrigger.inFlight()).thenReturn(emptySet())

        // When: only the second consecutive sighting acts, so the sweeper cannot race a
        // calculation that was stored moments before the sweep
        sweeper.sweepStalePendingCalculations()
        sweeper.sweepStalePendingCalculations()

        // Then
        verify(reportCalculationTrigger, times(1)).trigger(eq("ReportCalculation:orphaned"))
    }

    @Test
    fun `should not re-trigger on the first sighting`() {
        // Given
        stored(calculation("ReportCalculation:just-created", ReportCalculationStatus.PENDING))
        whenever(reportCalculationTrigger.inFlight()).thenReturn(emptySet())

        // When
        sweeper.sweepStalePendingCalculations()

        // Then
        verify(reportCalculationTrigger, never()).trigger(any())
    }

    @Test
    fun `should leave a pending calculation alone while it is queued on the executor`() {
        // Given a calculation waiting its turn is PENDING but not orphaned - re-triggering it
        // would duplicate the SQS load it is queued for
        stored(calculation("ReportCalculation:queued", ReportCalculationStatus.PENDING))
        whenever(reportCalculationTrigger.inFlight()).thenReturn(setOf("ReportCalculation:queued"))

        // When
        sweeper.sweepStalePendingCalculations()
        sweeper.sweepStalePendingCalculations()

        // Then
        verify(reportCalculationTrigger, never()).trigger(any())
    }

    @Test
    fun `should stop re-triggering once the calculation is no longer pending`() {
        // Given
        whenever(reportCalculationTrigger.inFlight()).thenReturn(emptySet())
        whenever(reportCalculationStorage.fetchAllReportCalculations())
            .thenReturn(listOf(calculation("ReportCalculation:1", ReportCalculationStatus.PENDING)))
            .thenReturn(listOf(calculation("ReportCalculation:1", ReportCalculationStatus.RUNNING)))

        // When
        sweeper.sweepStalePendingCalculations()
        sweeper.sweepStalePendingCalculations()

        // Then
        verify(reportCalculationTrigger, never()).trigger(any())
    }

    @Test
    fun `should ignore calculations that are not pending`() {
        // Given
        stored(
            calculation("ReportCalculation:running", ReportCalculationStatus.RUNNING),
            calculation("ReportCalculation:done", ReportCalculationStatus.FINISHED_SUCCESS),
            calculation("ReportCalculation:failed", ReportCalculationStatus.FINISHED_ERROR)
        )
        whenever(reportCalculationTrigger.inFlight()).thenReturn(emptySet())

        // When
        sweeper.sweepStalePendingCalculations()
        sweeper.sweepStalePendingCalculations()

        // Then
        verify(reportCalculationTrigger, never()).trigger(any())
    }
}
