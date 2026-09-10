package com.aamdigital.aambackendservice.reporting.report.core

import com.aamdigital.aambackendservice.common.changes.DocumentChangeEvent
import com.aamdigital.aambackendservice.common.changes.DocumentChangeHandler
import com.aamdigital.aambackendservice.reporting.reportcalculation.core.CreateReportCalculationRequest
import com.aamdigital.aambackendservice.reporting.reportcalculation.core.ReportCalculationDebouncer
import com.aamdigital.aambackendservice.reporting.webhook.storage.WebhookSubscriptionCache
import org.slf4j.LoggerFactory

/**
 * Recalculates the reports affected by a document change, for the reports a webhook subscribed to.
 *
 * Runs on the change-detection thread for every changed document, so everything it needs is
 * answered from memory: [ReportConfigCache] holds the report definitions and their analysed entity
 * types, and [WebhookSubscriptionCache] holds the subscribed report ids. The only work left inline
 * is recording the trigger with [ReportCalculationDebouncer], which is an in-memory map - the
 * calculation itself is created later by the debounce job and executed on its own bounded executor.
 */
class ReportDocumentChangeHandler(
    private val reportCalculationDebouncer: ReportCalculationDebouncer,
    private val identifyAffectedReportsUseCase: IdentifyAffectedReportsUseCase,
    private val webhookSubscriptionCache: WebhookSubscriptionCache
) : DocumentChangeHandler {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun handle(event: DocumentChangeEvent) {
        val affectedReports = identifyAffectedReportsUseCase.analyse(documentChangeEvent = event)

        if (affectedReports.isEmpty()) {
            return
        }

        val subscribedReportIds = webhookSubscriptionCache.subscribedReportIds()

        affectedReports
            .filter { report ->
                // we only need to do automatic calculations for reports that are subscribed to
                subscribedReportIds.contains(report.id)
            }.forEach { report ->
                logger.trace("recording debounced calculation trigger for report {}", report.id)
                // debounced: the calculation is only created once changes settle down,
                // so bursts of document changes result in a single recalculation
                reportCalculationDebouncer.recordChange(
                    request =
                        CreateReportCalculationRequest(
                            report = report,
                            args = mutableMapOf(),
                            fromAutomaticChangeDetection = true
                        )
                )
            }
    }
}
