package com.aamdigital.aambackendservice.reporting.report.jobs

import com.aamdigital.aambackendservice.reporting.report.core.ReportCalculationProcessor
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.Scheduled

@Configuration
class ReportCalculationJob(
    private val reportCalculationProcessor: ReportCalculationProcessor
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelay = 10000)
    fun handleReportCalculation() {
        reportCalculationProcessor.processNextPendingCalculation()
            .doOnError {
                logger.error("[ReportCalculationJob] Error in job: processNextPendingCalculation()", it)
            }
            .subscribe()
    }
}
