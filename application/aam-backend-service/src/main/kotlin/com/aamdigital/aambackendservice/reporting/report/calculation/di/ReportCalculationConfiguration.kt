package com.aamdigital.aambackendservice.reporting.report.calculation.di

import com.aamdigital.aambackendservice.reporting.notification.core.NotificationService
import com.aamdigital.aambackendservice.reporting.report.calculation.core.DefaultReportCalculationChangeUseCase
import com.aamdigital.aambackendservice.reporting.report.calculation.core.ReportCalculationChangeUseCase
import com.aamdigital.aambackendservice.reporting.report.core.ReportingStorage
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ReportCalculationConfiguration {

    @Bean
    fun defaultReportCalculationChangeUseCase(
        reportingStorage: ReportingStorage,
        objectMapper: ObjectMapper,
        notificationService: NotificationService,
    ): ReportCalculationChangeUseCase =
        DefaultReportCalculationChangeUseCase(reportingStorage, objectMapper, notificationService)
}
