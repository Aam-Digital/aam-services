package com.aamdigital.aambackendservice.reporting.reportcalculation.di

import com.aamdigital.aambackendservice.reporting.ConditionalOnReportingEnabled
import com.aamdigital.aambackendservice.reporting.reportcalculation.core.ReportCalculationChangeUseCase
import com.aamdigital.aambackendservice.reporting.reportcalculation.queue.RabbitMqReportCalculationEventPublisher
import com.aamdigital.aambackendservice.reporting.reportcalculation.queue.ReportCalculationCompletedEventConsumer
import com.aamdigital.aambackendservice.reporting.reportcalculation.queue.ReportCalculationEventListener
import com.aamdigital.aambackendservice.reporting.reportcalculation.usecase.DefaultReportCalculationUseCase
import com.fasterxml.jackson.databind.ObjectMapper
import io.micrometer.observation.ObservationRegistry
import org.springframework.amqp.core.Binding
import org.springframework.amqp.core.BindingBuilder
import org.springframework.amqp.core.FanoutExchange
import org.springframework.amqp.core.Queue
import org.springframework.amqp.core.QueueBuilder
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnReportingEnabled
class ReportCalculationQueueConfiguration {
    companion object {
        const val REPORT_CALCULATION_EVENT_QUEUE = "report.calculation"
        const val REPORT_CALCULATION_COMPLETED_QUEUE = "report.calculation.completed"
        const val REPORT_CALCULATION_COMPLETED_DEAD_LETTER_QUEUE = "$REPORT_CALCULATION_COMPLETED_QUEUE.deadLetter"
        const val REPORT_CALCULATION_COMPLETED_DEAD_LETTER_EXCHANGE = "$REPORT_CALCULATION_COMPLETED_QUEUE.dlx"
    }

    @Bean("report-calculation-event-queue")
    fun notificationQueue(): Queue =
        QueueBuilder
            .durable(REPORT_CALCULATION_EVENT_QUEUE)
            .build()

    @Bean("report-calculation-completed-queue")
    fun reportCalculationCompletedQueue(): Queue =
        QueueBuilder
            .durable(REPORT_CALCULATION_COMPLETED_QUEUE)
            .deadLetterExchange(REPORT_CALCULATION_COMPLETED_DEAD_LETTER_EXCHANGE)
            .build()

    @Bean("report-calculation-completed-dead-letter-queue")
    fun reportCalculationCompletedDeadLetterQueue(): Queue =
        QueueBuilder
            .durable(REPORT_CALCULATION_COMPLETED_DEAD_LETTER_QUEUE)
            .build()

    @Bean("report-calculation-completed-dead-letter-exchange")
    fun reportCalculationCompletedDeadLetterExchange(): FanoutExchange =
        FanoutExchange(REPORT_CALCULATION_COMPLETED_DEAD_LETTER_EXCHANGE)

    @Bean
    fun reportCalculationCompletedDeadLetterBinding(
        @Qualifier("report-calculation-completed-dead-letter-queue") reportCalculationCompletedDeadLetterQueue: Queue,
        @Qualifier("report-calculation-completed-dead-letter-exchange") reportCalculationCompletedDeadLetterExchange: FanoutExchange
    ): Binding = BindingBuilder.bind(reportCalculationCompletedDeadLetterQueue).to(reportCalculationCompletedDeadLetterExchange)
    @Bean
    fun reportCalculationEventListener(
        observationRegistry: ObservationRegistry,
        reportCalculationUseCase: DefaultReportCalculationUseCase,
        objectMapper: ObjectMapper,
        reportCalculationEventPublisher: RabbitMqReportCalculationEventPublisher
    ): ReportCalculationEventListener =
        ReportCalculationEventListener(
            observationRegistry,
            reportCalculationUseCase,
            objectMapper,
            reportCalculationEventPublisher
        )

    @Bean
    fun reportCalculationCompletedEventConsumer(
        observationRegistry: ObservationRegistry,
        reportCalculationChangeUseCase: ReportCalculationChangeUseCase
    ): ReportCalculationCompletedEventConsumer =
        ReportCalculationCompletedEventConsumer(observationRegistry, reportCalculationChangeUseCase)
}

