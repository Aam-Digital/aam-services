package com.aamdigital.aambackendservice.reporting.reportcalculation.di

import com.aamdigital.aambackendservice.reporting.ConditionalOnReportingEnabled
import com.aamdigital.aambackendservice.reporting.reportcalculation.core.ReportCalculationChangeUseCase
import com.aamdigital.aambackendservice.reporting.reportcalculation.queue.ReportCalculationEventListener
import com.aamdigital.aambackendservice.reporting.reportcalculation.usecase.DefaultReportCalculationUseCase
import com.fasterxml.jackson.databind.ObjectMapper
import io.micrometer.observation.ObservationRegistry
import org.springframework.amqp.core.Queue
import org.springframework.amqp.core.QueueBuilder
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

@Configuration
@ConditionalOnReportingEnabled
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
        objectMapper: ObjectMapper,
        reportCalculationChangeUseCase: ReportCalculationChangeUseCase,
        @Value("\${report-calculation-completion.retry-attempts:3}") completionRetryAttempts: Int,
        @Value("\${report-calculation-completion.retry-initial-interval-millis:1000}")
        completionRetryInitialIntervalMillis: Long
    ): ReportCalculationEventListener =
        ReportCalculationEventListener(
            observationRegistry,
            reportCalculationUseCase,
            objectMapper,
            reportCalculationChangeUseCase,
            completionRetryAttempts,
            Duration.ofMillis(completionRetryInitialIntervalMillis)
        )
}

