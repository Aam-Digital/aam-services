package com.aamdigital.aambackendservice.reporting.reportcalculation.di

import com.aamdigital.aambackendservice.reporting.reportcalculation.queue.ReportCalculationEventListener
import com.aamdigital.aambackendservice.reporting.reportcalculation.usecase.DefaultReportCalculationUseCase
import com.fasterxml.jackson.databind.ObjectMapper
import io.micrometer.observation.ObservationRegistry
import org.springframework.amqp.core.Queue
import org.springframework.amqp.core.QueueBuilder
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnProperty(
    prefix = "features.reporting",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = false
)
class ReportCalculationQueueConfiguration {
    companion object {
        const val REPORT_CALCULATION_EVENT_QUEUE = "report.calculation"
    }

    @Bean("report-calculation-event-queue")
    fun notificationQueue(): Queue =
        QueueBuilder
            .durable(REPORT_CALCULATION_EVENT_QUEUE)
            .build()

    @Bean
    fun reportCalculationEventListener(
        observationRegistry: ObservationRegistry,
        reportCalculationUseCase: DefaultReportCalculationUseCase,
        objectMapper: ObjectMapper
    ): ReportCalculationEventListener =
        ReportCalculationEventListener(observationRegistry, reportCalculationUseCase, objectMapper)
}
