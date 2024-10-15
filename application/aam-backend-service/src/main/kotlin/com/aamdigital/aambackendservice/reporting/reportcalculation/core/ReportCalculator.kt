package com.aamdigital.aambackendservice.reporting.reportcalculation.core

import com.aamdigital.aambackendservice.reporting.domain.ReportCalculation

interface ReportCalculator {
    fun calculate(reportCalculation: ReportCalculation): ReportCalculation
}
