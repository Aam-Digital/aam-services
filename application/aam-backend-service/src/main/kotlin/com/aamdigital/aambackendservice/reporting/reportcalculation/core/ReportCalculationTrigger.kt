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
}
