package com.aamdigital.aambackendservice.reporting.reportcalculation.core

interface ReportCalculationChangeUseCase {
    /**
     * React to a report calculation that has finished: if its result differs from the previous
     * successful run, trigger the subscribed webhooks (otherwise drop a redundant auto-created run).
     */
    fun handle(reportCalculationId: String)
}
