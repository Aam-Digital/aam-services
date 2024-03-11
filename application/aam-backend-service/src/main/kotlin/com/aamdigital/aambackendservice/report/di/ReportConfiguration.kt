package com.aamdigital.aambackendservice.report.di

import com.aamdigital.aambackendservice.report.calculation.core.ReportCalculator
import com.aamdigital.aambackendservice.report.core.DefaultIdentifyAffectedReportsUseCase
import com.aamdigital.aambackendservice.report.core.DefaultReportCalculationProcessor
import com.aamdigital.aambackendservice.report.core.IdentifyAffectedReportsUseCase
import com.aamdigital.aambackendservice.report.core.NoopReportCalculationProcessor
import com.aamdigital.aambackendservice.report.core.ReportCalculationProcessor
import com.aamdigital.aambackendservice.report.core.ReportSchemaGenerator
import com.aamdigital.aambackendservice.report.core.ReportingStorage
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
