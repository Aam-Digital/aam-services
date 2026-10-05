package com.aamdigital.aambackendservice.reporting.reportcalculation.job

import com.aamdigital.aambackendservice.common.scheduling.ScheduledJobBackoff
import com.aamdigital.aambackendservice.reporting.ConditionalOnReportingEnabled
import com.aamdigital.aambackendservice.reporting.reportcalculation.core.ReportCalculationSweeper
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.Scheduled

/**
 * Scheduled trigger for [ReportCalculationSweeper], which restarts report calculations that were
 * stored but never executed.
 *
 * Runs infrequently: it is a recovery path for a lost trigger, not part of normal operation.
 */
@Configuration
@ConditionalOnReportingEnabled
class ReportCalculationSweepJob(
    private val reportCalculationSweeper: ReportCalculationSweeper
) {
    companion object {
        const val MAX_BACKOFF_MS = 3_600_000L // 1 hour
    }

    private val logger = LoggerFactory.getLogger(javaClass)
    internal val backoff =
        ScheduledJobBackoff(logger, "ReportCalculationSweepJob", maxBackoffMs = MAX_BACKOFF_MS)

    @Scheduled(fixedDelayString = "\${report-calculation-sweeper.fixed-delay:300000}")
    fun sweepStalePendingReportCalculations() {
        backoff.run {
            reportCalculationSweeper.sweepStalePendingCalculations()
        }
    }
}
