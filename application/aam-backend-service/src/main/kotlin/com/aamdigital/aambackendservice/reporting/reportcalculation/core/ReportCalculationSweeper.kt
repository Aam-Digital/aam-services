package com.aamdigital.aambackendservice.reporting.reportcalculation.core

import com.aamdigital.aambackendservice.reporting.reportcalculation.ReportCalculationStatus
import org.slf4j.LoggerFactory

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
 * A calculation document carries no creation timestamp - `calculationStarted` is only set on the
 * move to `RUNNING` - so age cannot be used to tell a stuck calculation from one that is simply
 * queued. Instead, a `PENDING` calculation counts as orphaned when
 * [ReportCalculationTrigger.inFlight] does not know about it, and it is only re-triggered once it
 * has looked orphaned on two consecutive sweeps. That second sighting is what keeps the sweeper
 * from racing a calculation that was stored moments before the sweep and had not yet been
 * submitted.
 *
 * After a restart nothing is in flight, so everything left `PENDING` is recovered - which is
 * exactly the case this exists for.
 */
class ReportCalculationSweeper(
    private val reportCalculationStorage: ReportCalculationStorage,
    private val reportCalculationTrigger: ReportCalculationTrigger
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    private var orphanedInPreviousSweep: Set<String> = emptySet()

    fun sweepStalePendingCalculations() {
        val inFlight = reportCalculationTrigger.inFlight()

        val orphaned =
            reportCalculationStorage
                .fetchAllReportCalculations()
                .filter { calculation -> calculation.status == ReportCalculationStatus.PENDING }
                .map { calculation -> calculation.id }
                .filterNot { id -> inFlight.contains(id) }
                .toSet()

        orphaned
            .filter { id -> orphanedInPreviousSweep.contains(id) }
            .forEach { id ->
                logger.warn("Report calculation {} is PENDING but nothing is running it, re-triggering", id)
                reportCalculationTrigger.trigger(id)
            }

        orphanedInPreviousSweep = orphaned
    }
}
