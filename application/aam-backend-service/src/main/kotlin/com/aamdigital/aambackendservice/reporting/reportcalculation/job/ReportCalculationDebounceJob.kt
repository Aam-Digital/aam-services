package com.aamdigital.aambackendservice.reporting.reportcalculation.job

import com.aamdigital.aambackendservice.reporting.ConditionalOnReportingEnabled
import com.aamdigital.aambackendservice.reporting.reportcalculation.core.ReportCalculationDebouncer
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.Scheduled

/**
 * Periodically flushes debounced report calculation triggers
 * (see [ReportCalculationDebouncer]). Failed creations stay registered in the
 * debouncer and are retried on the next flush, so no backoff handling is needed here.
 */
@Configuration
@ConditionalOnReportingEnabled
class ReportCalculationDebounceJob(
    private val reportCalculationDebouncer: ReportCalculationDebouncer,
) {
    @Scheduled(fixedDelayString = "\${report-calculation-debounce.flush-fixed-delay:10000}")
    fun flushDueReportCalculations() {
        reportCalculationDebouncer.flushDueTriggers()
    }
}
