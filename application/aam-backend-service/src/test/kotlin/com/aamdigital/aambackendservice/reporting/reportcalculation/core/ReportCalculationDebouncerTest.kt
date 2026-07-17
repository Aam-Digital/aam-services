package com.aamdigital.aambackendservice.reporting.reportcalculation.core

import com.aamdigital.aambackendservice.common.domain.DomainReference
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.capture
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReportCalculationDebouncerTest {
    private class MutableClock(
        private var current: Instant
    ) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = this

        override fun instant(): Instant = current

        fun advanceBy(duration: Duration) {
            current += duration
        }
    }

    private lateinit var service: ReportCalculationDebouncer
    private lateinit var clock: MutableClock

    @Mock
    lateinit var createReportCalculationUseCase: CreateReportCalculationUseCase

    @Captor
    lateinit var requestCaptor: ArgumentCaptor<CreateReportCalculationRequest>

    private val quietPeriod: Duration = Duration.ofSeconds(60)
    private val maxWait: Duration = Duration.ofSeconds(300)

    @BeforeEach
    fun setUp() {
        reset(createReportCalculationUseCase)
        whenever(createReportCalculationUseCase.createReportCalculation(any()))
            .thenReturn(CreateReportCalculationResult.Success(DomainReference("ReportCalculation:1")))

        clock = MutableClock(Instant.parse("2026-01-01T00:00:00Z"))
        service =
            ReportCalculationDebouncer(
                createReportCalculationUseCase = createReportCalculationUseCase,
                quietPeriod = quietPeriod,
                maxWait = maxWait,
                clock = clock,
            )
    }

    private fun requestFor(reportId: String): CreateReportCalculationRequest =
        CreateReportCalculationRequest(
            report = DomainReference(reportId),
            args = mutableMapOf(),
            fromAutomaticChangeDetection = true
        )

    @Test
    fun `should coalesce a burst of changes into a single calculation after the quiet period`() {
        // given
        repeat(5) {
            service.recordChange(requestFor("ReportConfig:report-a"))
            clock.advanceBy(Duration.ofMillis(100))
        }

        // when
        clock.advanceBy(quietPeriod)
        service.flushDueTriggers()

        // then
        verify(createReportCalculationUseCase, times(1)).createReportCalculation(capture(requestCaptor))
        assertThat(requestCaptor.value.report.id).isEqualTo("ReportConfig:report-a")
        assertThat(requestCaptor.value.fromAutomaticChangeDetection).isTrue()
    }

    @Test
    fun `should not create a calculation before the quiet period has elapsed`() {
        // given
        service.recordChange(requestFor("ReportConfig:report-a"))

        // when
        clock.advanceBy(quietPeriod.minusSeconds(1))
        service.flushDueTriggers()

        // then
        verify(createReportCalculationUseCase, never()).createReportCalculation(any())
    }

    @Test
    fun `should extend the wait while new changes keep arriving (rolling debounce)`() {
        // given
        service.recordChange(requestFor("ReportConfig:report-a"))
        clock.advanceBy(Duration.ofSeconds(45))
        service.flushDueTriggers()
        service.recordChange(requestFor("ReportConfig:report-a"))

        // when: quiet period since the FIRST change has elapsed, but not since the latest one
        clock.advanceBy(Duration.ofSeconds(45))
        service.flushDueTriggers()

        // then
        verify(createReportCalculationUseCase, never()).createReportCalculation(any())

        // when: quiet period since the latest change has elapsed
        clock.advanceBy(Duration.ofSeconds(20))
        service.flushDueTriggers()

        // then
        verify(createReportCalculationUseCase, times(1)).createReportCalculation(any())
    }

    @Test
    fun `should create a calculation after max wait even when changes arrive continuously`() {
        // given: a change every 30s keeps the quiet period from ever elapsing
        service.recordChange(requestFor("ReportConfig:report-a"))

        // when
        repeat(10) {
            clock.advanceBy(Duration.ofSeconds(30))
            service.recordChange(requestFor("ReportConfig:report-a"))
            service.flushDueTriggers()
        }

        // then: created exactly once, when maxWait since the first change was reached
        verify(createReportCalculationUseCase, times(1)).createReportCalculation(any())
    }

    @Test
    fun `should debounce reports independently`() {
        // given
        service.recordChange(requestFor("ReportConfig:report-a"))
        clock.advanceBy(quietPeriod)
        service.recordChange(requestFor("ReportConfig:report-b"))

        // when
        service.flushDueTriggers()

        // then: only report-a is due; report-b was changed just now
        verify(createReportCalculationUseCase, times(1)).createReportCalculation(capture(requestCaptor))
        assertThat(requestCaptor.value.report.id).isEqualTo("ReportConfig:report-a")
    }

    @Test
    fun `should retry on the next flush when creating the calculation fails`() {
        // given
        whenever(createReportCalculationUseCase.createReportCalculation(any()))
            .thenReturn(
                CreateReportCalculationResult.Failure(
                    errorCode = CreateReportCalculationResult.ErrorCode.INTERNAL_SERVER_ERROR
                )
            ).thenReturn(CreateReportCalculationResult.Success(DomainReference("ReportCalculation:1")))
        service.recordChange(requestFor("ReportConfig:report-a"))
        clock.advanceBy(quietPeriod)

        // when
        service.flushDueTriggers()
        service.flushDueTriggers()
        service.flushDueTriggers()

        // then: first attempt failed and was retried once; after success nothing is pending anymore
        verify(createReportCalculationUseCase, times(2)).createReportCalculation(any())
    }

    @Test
    fun `should create pending calculations immediately on flushAll (shutdown)`() {
        // given: changes recorded just now, quiet period NOT elapsed
        service.recordChange(requestFor("ReportConfig:report-a"))
        service.recordChange(requestFor("ReportConfig:report-b"))

        // when
        service.flushAll()

        // then
        verify(createReportCalculationUseCase, times(2)).createReportCalculation(any())
    }
}
