package com.aamdigital.aambackendservice.reporting.reportcalculation.di

import org.springframework.amqp.core.Queue
import org.springframework.amqp.core.QueueBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration


@Configuration
class ReportCalculationQueueConfiguration {

    companion object {
        const val REPORT_CALCULATION_EVENT_QUEUE = "report.calculation"
    }

    @Bean("report-calculation-event-queue")
    fun notificationQueue(): Queue = QueueBuilder
        .durable(REPORT_CALCULATION_EVENT_QUEUE)
        .build()
}
