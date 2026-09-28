package com.aamdigital.aambackendservice.reporting.reportcalculation.core

import com.aamdigital.aambackendservice.common.execution.BoundedTaskRunner
import org.slf4j.LoggerFactory

/**
 * Runs report calculations through a [BoundedTaskRunner].
 *
 * The bound is the point: a calculation holds an SQS query open for seconds to minutes, and SQS is
 * effectively single-threaded, so only a few may run at once.
 *
 * A rejected submission leaves the calculation `PENDING`, which [ReportCalculationSweeper] picks up
 * later, so the calculation document is the record that the work is still owed.
 */
class ExecutorReportCalculationTrigger(
    private val reportCalculationRunner: BoundedTaskRunner,
    private val reportCalculationProcessor: ReportCalculationProcessor
) : ReportCalculationTrigger {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun inFlight(): Set<String> = reportCalculationRunner.inFlight()

    override fun trigger(reportCalculationId: String) {
        val accepted =
            reportCalculationRunner.submit(reportCalculationId) {
                reportCalculationProcessor.process(reportCalculationId)
            }

        if (!accepted) {
            logger.warn(
                "Report calculation {} was not started because the executor is saturated or shutting " +
                    "down; it stays PENDING and will be picked up again",
                reportCalculationId
            )
        }
    }
}
