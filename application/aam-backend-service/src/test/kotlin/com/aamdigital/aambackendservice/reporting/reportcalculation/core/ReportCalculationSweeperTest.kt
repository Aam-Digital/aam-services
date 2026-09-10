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
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

@ExtendWith(MockitoExtension::class)
class ReportCalculationSweeperTest {
    @Mock
    lateinit var reportCalculationStorage: ReportCalculationStorage

    @Mock
    lateinit var reportCalculationTrigger: ReportCalculationTrigger

    private val now: Instant = Instant.parse("2026-01-01T12:00:00Z")
    private val clock: Clock =
        object : Clock() {
            override fun instant(): Instant = now

            override fun getZone(): ZoneOffset = ZoneOffset.UTC

            override fun withZone(zone: ZoneId): Clock = this
        }

    private val sweeper by lazy {
        ReportCalculationSweeper(
            reportCalculationStorage = reportCalculationStorage,
            reportCalculationTrigger = reportCalculationTrigger,
            staleAfter = Duration.ofMinutes(15),
            clock = clock
        )
    }

    private fun calculation(
        id: String,
        status: ReportCalculationStatus,
        startedAt: String? = null
    ) = ReportCalculation(
        id = id,
        report = DomainReference("ReportConfig:1"),
        status = status
    ).also { it.calculationStarted = startedAt }

    @Test
    fun `should re-trigger a calculation that is still pending with no start date`() {
        // Given a calculation is stored before the executor is asked to run it, so a crash in
        // between leaves a PENDING document nothing is working on
        whenever(reportCalculationStorage.fetchAllReportCalculations())
            .thenReturn(listOf(calculation("ReportCalculation:stuck", ReportCalculationStatus.PENDING)))

        // When
        sweeper.sweepStalePendingCalculations()

        // Then
        verify(reportCalculationTrigger).trigger(eq("ReportCalculation:stuck"))
    }

    @Test
    fun `should leave a pending calculation alone until it is stale`() {
        // Given a calculation that is simply waiting its turn on the executor
        whenever(reportCalculationStorage.fetchAllReportCalculations())
            .thenReturn(
                listOf(
                    calculation(
                        "ReportCalculation:queued",
                        ReportCalculationStatus.PENDING,
                        startedAt = now.minus(Duration.ofMinutes(1)).toString()
                    )
                )
            )

        // When
        sweeper.sweepStalePendingCalculations()

        // Then
        verify(reportCalculationTrigger, never()).trigger(any())
    }

    @Test
    fun `should re-trigger a pending calculation once it is older than the stale threshold`() {
        // Given
        whenever(reportCalculationStorage.fetchAllReportCalculations())
            .thenReturn(
                listOf(
                    calculation(
                        "ReportCalculation:old",
                        ReportCalculationStatus.PENDING,
                        startedAt = now.minus(Duration.ofMinutes(30)).toString()
                    )
                )
            )

        // When
        sweeper.sweepStalePendingCalculations()

        // Then
        verify(reportCalculationTrigger).trigger(eq("ReportCalculation:old"))
    }

    @Test
    fun `should ignore calculations that are not pending`() {
        // Given
        whenever(reportCalculationStorage.fetchAllReportCalculations())
            .thenReturn(
                listOf(
                    calculation("ReportCalculation:running", ReportCalculationStatus.RUNNING),
                    calculation("ReportCalculation:done", ReportCalculationStatus.FINISHED_SUCCESS),
                    calculation("ReportCalculation:failed", ReportCalculationStatus.FINISHED_ERROR)
                )
            )

        // When
        sweeper.sweepStalePendingCalculations()

        // Then
        verify(reportCalculationTrigger, never()).trigger(any())
    }
}
