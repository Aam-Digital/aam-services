package com.aamdigital.aambackendservice.reporting.reportcalculation.core

import com.aamdigital.aambackendservice.reporting.reportcalculation.ReportCalculationStatus
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * Re-triggers report calculations that were stored as `PENDING` but never ran.
 *
 * `CreateReportCalculationUseCase` writes the calculation document first and then asks
 * [ReportCalculationTrigger] to run it, so a crash, a shutdown or a saturated executor in between
 * leaves a `PENDING` document that nothing is working on. That would be permanent without this
 * sweeper, and worse than merely losing one run: the "is there already a PENDING calculation for
 * this report and these arguments?" check in `CreateReportCalculationUseCase` treats such a
 * document as in flight, so no further calculation for that report would ever be created.
 *
 * A calculation is only re-triggered once it has been `PENDING` for longer than [staleAfter], so a
 * calculation that is simply waiting its turn on the executor is left alone.
 */
class ReportCalculationSweeper(
    private val reportCalculationStorage: ReportCalculationStorage,
    private val reportCalculationTrigger: ReportCalculationTrigger,
    private val staleAfter: Duration,
    private val clock: Clock = Clock.systemUTC()
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun sweepStalePendingCalculations() {
        val cutoff = clock.instant().minus(staleAfter)

        reportCalculationStorage
            .fetchAllReportCalculations()
            .filter { calculation -> calculation.status == ReportCalculationStatus.PENDING }
            .filter { calculation -> createdBefore(calculation.id, calculation.calculationStarted, cutoff) }
            .forEach { calculation ->
                logger.warn(
                    "Report calculation {} has been PENDING since before {}, re-triggering it",
                    calculation.id,
                    cutoff
                )
                reportCalculationTrigger.trigger(calculation.id)
            }
    }

    /**
     * A `PENDING` calculation has no start date yet, so there is no timestamp on the document to
     * compare against. Treating a missing date as stale is safe: re-triggering is idempotent
     * (`ReportCalculationUseCase` re-runs it and rewrites the same document) and only a calculation
     * still `PENDING` at sweep time is considered at all.
     */
    private fun createdBefore(
        reportCalculationId: String,
        calculationStarted: String?,
        cutoff: Instant
    ): Boolean {
        if (calculationStarted.isNullOrBlank()) {
            return true
        }

        return try {
            Instant.parse(calculationStarted).isBefore(cutoff)
        } catch (ex: Exception) {
            logger.debug(
                "Could not read the start date of report calculation {}, treating it as stale",
                reportCalculationId,
                ex
            )
            true
        }
    }
}
