package com.aamdigital.aambackendservice.reporting.reportcalculation

import com.aamdigital.aambackendservice.common.events.DomainEvent

/**
 * Starts the processing of a ReportCalculation
 */
class ReportCalculationEvent(
//    val tenant: String, // prepare tenant support
    val reportCalculationId: String,
) : DomainEvent()
