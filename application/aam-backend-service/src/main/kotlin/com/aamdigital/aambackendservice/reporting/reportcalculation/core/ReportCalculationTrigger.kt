package com.aamdigital.aambackendservice.reporting.reportcalculation.core

/**
 * Starts the execution of a stored, still `PENDING` report calculation.
 *
 * This is the seam that used to be the `report.calculation` queue. The calculation document is
 * written before the trigger is called, so the document - not this call - is the record that the
 * calculation is owed; see [ReportCalculationSweeper] for what picks one up again if the trigger is
 * lost.
 */
interface ReportCalculationTrigger {
    fun trigger(reportCalculationId: String)

    /**
     * Ids this process has accepted and not yet finished.
     *
     * A `PENDING` calculation that is in here is simply waiting its turn or running; one that is
     * not has been orphaned. [ReportCalculationSweeper] uses this to tell the two apart, because
     * the calculation document carries no creation timestamp to age it by
     * (`calculationStarted` is only set on the move to `RUNNING`).
     */
    fun inFlight(): Set<String>
}
