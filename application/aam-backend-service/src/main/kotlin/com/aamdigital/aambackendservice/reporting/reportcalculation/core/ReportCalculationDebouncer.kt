package com.aamdigital.aambackendservice.reporting.reportcalculation.core

import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Coalesces bursts of document changes into a single report calculation per report
 * ("rolling debounce").
 *
 * Without debouncing, every changed document triggers its own calculation for each affected
 * report. During normal editing or bulk imports this queues many redundant back-to-back
 * calculations that each block SQS for several seconds, even though only the final state of
 * the database matters (a calculation reads the database at execution time, so the last one
 * always reflects all previous changes).
 *
 * [recordChange] only remembers that a report needs recalculation; the calculation is created
 * once no further change has arrived for [quietPeriod]. While changes keep arriving, the wait
 * keeps extending, capped by [maxWait] since the first pending change so that consumers still
 * get regular intermediate results during long-running imports.
 *
 * [flushDueTriggers] must be invoked periodically by a scheduled job. Pending triggers are
 * held in memory only; on shutdown they are flushed immediately (best-effort).
 */
class ReportCalculationDebouncer(
    private val createReportCalculationUseCase: CreateReportCalculationUseCase,
    private val quietPeriod: Duration,
    private val maxWait: Duration,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    private class PendingTrigger(
        val request: CreateReportCalculationRequest,
        val firstChangeAt: Instant,
        @Volatile var lastChangeAt: Instant,
    )

    private val pendingTriggers = ConcurrentHashMap<String, PendingTrigger>()

    fun recordChange(request: CreateReportCalculationRequest) {
        val now = clock.instant()
        pendingTriggers.compute(request.report.id) { _, existing ->
            existing?.also { it.lastChangeAt = now }
                ?: PendingTrigger(request, firstChangeAt = now, lastChangeAt = now)
        }
    }

    fun flushDueTriggers() {
        val now = clock.instant()
        pendingTriggers.forEach { (reportId, trigger) ->
            val quietPeriodElapsed = !now.isBefore(trigger.lastChangeAt.plus(quietPeriod))
            val maxWaitExceeded = !now.isBefore(trigger.firstChangeAt.plus(maxWait))
            if (quietPeriodElapsed || maxWaitExceeded) {
                triggerCalculation(reportId, trigger)
            }
        }
    }

    @PreDestroy
    fun flushAll() {
        pendingTriggers.forEach { (reportId, trigger) ->
            triggerCalculation(reportId, trigger)
        }
    }

    private fun triggerCalculation(
        reportId: String,
        trigger: PendingTrigger
    ) {
        // claim the trigger before creating, so a change arriving concurrently starts a fresh
        // debounce window instead of being swallowed by the calculation we create now
        pendingTriggers.remove(reportId)

        try {
            when (val result = createReportCalculationUseCase.createReportCalculation(trigger.request)) {
                is CreateReportCalculationResult.Success -> {
                    logger.debug(
                        "created debounced report calculation {} for report {}",
                        result.calculation.id,
                        reportId,
                    )
                }

                is CreateReportCalculationResult.Failure -> {
                    logger.warn(
                        "could not create debounced report calculation for report {} ({}), will retry: {}",
                        reportId,
                        result.errorCode,
                        result.errorMessage,
                        result.cause,
                    )
                    restorePendingTrigger(reportId, trigger)
                }
            }
        } catch (ex: Exception) {
            // an unexpected throw (not a Failure result) must not lose the claimed trigger, nor
            // abort the flush loop over the remaining reports: restore for retry on the next flush
            logger.error(
                "unexpected error creating debounced report calculation for report {}, will retry",
                reportId,
                ex,
            )
            restorePendingTrigger(reportId, trigger)
        }
    }

    /** Re-registers a failed trigger, merging with any change recorded in the meantime. */
    private fun restorePendingTrigger(
        reportId: String,
        trigger: PendingTrigger
    ) {
        pendingTriggers.compute(reportId) { _, newer ->
            newer?.also { trigger.lastChangeAt = maxOf(trigger.lastChangeAt, it.lastChangeAt) }
            trigger
        }
    }
}
