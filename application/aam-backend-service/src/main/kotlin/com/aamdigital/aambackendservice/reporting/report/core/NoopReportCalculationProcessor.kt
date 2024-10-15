package com.aamdigital.aambackendservice.reporting.report.core

import org.slf4j.LoggerFactory

class NoopReportCalculationProcessor : ReportCalculationProcessor {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun processNextPendingCalculation() {
        logger.trace("[NoopReportCalculationProcessor] processNextPendingCalculation() called.")
    }
}
