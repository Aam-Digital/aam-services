package com.aamdigital.aambackendservice.reporting.report.di

import com.aamdigital.aambackendservice.reporting.report.core.DefaultIdentifyAffectedReportsUseCase
import com.aamdigital.aambackendservice.reporting.report.core.DefaultReportCalculationProcessor
import com.aamdigital.aambackendservice.reporting.report.core.IdentifyAffectedReportsUseCase
import com.aamdigital.aambackendservice.reporting.report.core.NoopReportCalculationProcessor
import com.aamdigital.aambackendservice.reporting.report.core.ReportCalculationProcessor
import com.aamdigital.aambackendservice.reporting.report.core.ReportSchemaGenerator
import com.aamdigital.aambackendservice.reporting.report.core.ReportingStorage
import com.aamdigital.aambackendservice.reporting.reportcalculation.core.ReportCalculator
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ReportConfiguration {

    @Bean
    fun defaultIdentifyAffectedReportsUseCase(
        reportingStorage: ReportingStorage,
        schemaGenerator: ReportSchemaGenerator,
    ): IdentifyAffectedReportsUseCase =
        DefaultIdentifyAffectedReportsUseCase(reportingStorage, schemaGenerator)

    @Bean
    @ConditionalOnProperty(
        prefix = "report-calculation-processor",
        name = ["enabled"],
        havingValue = "false"
    )
    fun noopReportCalculationProcessor(): ReportCalculationProcessor = NoopReportCalculationProcessor()

    @Bean
    @ConditionalOnProperty(
        prefix = "report-calculation-processor",
        name = ["enabled"],
        matchIfMissing = true
    )
    fun defaultReportCalculationProcessor(
        reportCalculator: ReportCalculator,
        reportingStorage: ReportingStorage,
    ): ReportCalculationProcessor = DefaultReportCalculationProcessor(
        reportCalculator = reportCalculator,
        reportingStorage = reportingStorage,
    )
}
