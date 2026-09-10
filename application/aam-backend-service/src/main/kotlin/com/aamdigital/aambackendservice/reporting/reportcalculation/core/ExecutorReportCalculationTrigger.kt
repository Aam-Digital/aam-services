package com.aamdigital.aambackendservice.reporting.reportcalculation.core

import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException

/**
 * Runs report calculations on a bounded executor.
 *
 * The bound is the point: a calculation holds an SQS query open for seconds to minutes, and SQS is
 * effectively single-threaded, so only a few may run at once. The executor's pool size reproduces
 * the concurrency cap the `report.calculation` queue's consumers used to provide.
 *
 * A rejected submission leaves the calculation `PENDING`, which [ReportCalculationSweeper] picks up
 * later - the same net effect as a message waiting in a queue, just recorded in the calculation
 * document instead of in a broker.
 */
class ExecutorReportCalculationTrigger(
    private val reportCalculationExecutor: Executor,
    private val reportCalculationProcessor: ReportCalculationProcessor
) : ReportCalculationTrigger {
    private val logger = LoggerFactory.getLogger(javaClass)

    /** Accepted but not yet finished, so the sweeper does not re-trigger work already queued. */
    private val inFlightCalculations: MutableSet<String> = ConcurrentHashMap.newKeySet()

    override fun inFlight(): Set<String> = inFlightCalculations.toSet()

    override fun trigger(reportCalculationId: String) {
        // marked before submitting, so a sweep running concurrently with the submission cannot see
        // this calculation as orphaned
        inFlightCalculations.add(reportCalculationId)

        try {
            reportCalculationExecutor.execute {
                try {
                    reportCalculationProcessor.process(reportCalculationId)
                } finally {
                    inFlightCalculations.remove(reportCalculationId)
                }
            }
        } catch (ex: RejectedExecutionException) {
            inFlightCalculations.remove(reportCalculationId)
            logger.warn(
                "Report calculation {} was not started because the executor is saturated or shutting " +
                    "down; it stays PENDING and will be picked up again: {}",
                reportCalculationId,
                ex.message
            )
        }
    }
}
