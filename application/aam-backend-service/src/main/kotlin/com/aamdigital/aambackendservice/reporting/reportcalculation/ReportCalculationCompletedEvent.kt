package com.aamdigital.aambackendservice.reporting.reportcalculation

import com.aamdigital.aambackendservice.common.events.DomainEvent

/**
 * Signals that a ReportCalculation has finished successfully.
 *
 * Published directly to RabbitMQ once the calculation is stored, so the webhook-notification path is
 * triggered by an explicit domain event instead of by observing the `report-calculation` database
 * through the CouchDB changes feed. This decouples the feature from the change-detection allowlist.
 */
class ReportCalculationCompletedEvent(
    val reportCalculationId: String
) : DomainEvent()
