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
    private val logger = LoggerFactory.getLogger(javaClass)
    internal val backoff = ScheduledJobBackoff(logger, "ReportCalculationSweepJob")

    @Scheduled(fixedDelayString = "\${report-calculation-sweeper.fixed-delay:300000}")
    fun sweepStalePendingReportCalculations() {
        if (backoff.shouldSkip()) return

        backoff.execute {
            reportCalculationSweeper.sweepStalePendingCalculations()
        }
    }
}
