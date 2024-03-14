package com.aamdigital.aambackendservice.domain

/**
 * Representation of a reference to another Domain Object.
 * Used, when just the Identifier is needed, not the hole object.
 *
 * @example You want to trigger a calculation for new Report
 * and just got the ReportId from your controller. You just pass a DomainReference to that Report:
 *
 * triggerCalculation(reportId: DomainReference): Unit {}
 *
 * const reportId = "r-1";
 * triggerCalculation(DomainReference(reportId));
 *
 */
data class DomainReference(
    val id: String
)
