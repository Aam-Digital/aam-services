package com.aamdigital.aambackendservice.report.di

import com.aamdigital.aambackendservice.queue.core.QueueMessageParser
import com.aamdigital.aambackendservice.report.calculation.core.CreateReportCalculationUseCase
import com.aamdigital.aambackendservice.report.calculation.core.ReportCalculationChangeUseCase
import com.aamdigital.aambackendservice.report.core.DefaultReportDocumentChangeEventConsumer
import com.aamdigital.aambackendservice.report.core.IdentifyAffectedReportsUseCase
import com.aamdigital.aambackendservice.report.core.ReportDocumentChangeEventConsumer
import org.springframework.amqp.core.Binding
import org.springframework.amqp.core.BindingBuilder
import org.springframework.amqp.core.FanoutExchange
import org.springframework.amqp.core.Queue
import org.springframework.amqp.core.QueueBuilder
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ReportQueueConfiguration {

    companion object {
        const val DOCUMENT_CHANGES_REPORT_QUEUE = "document.changes.report"
    }

    @Bean("report-config-changes-queue")
    fun reportConfigChangesQueue(): Queue = QueueBuilder
        .durable(DOCUMENT_CHANGES_REPORT_QUEUE)
        .build()

    @Bean
    fun documentChangesBinding(
        @Qualifier("report-config-changes-queue") queue: Queue,
        @Qualifier("document-changes-exchange") exchange: FanoutExchange,
    ): Binding = BindingBuilder.bind(queue).to(exchange)

    @Bean
    fun reportDocumentChangeEventConsumer(
        messageParser: QueueMessageParser,
        createReportCalculationUseCase: CreateReportCalculationUseCase,
        reportCalculationChangeUseCase: ReportCalculationChangeUseCase,
        identifyAffectedReportsUseCase: IdentifyAffectedReportsUseCase,
    ): ReportDocumentChangeEventConsumer =
        DefaultReportDocumentChangeEventConsumer(
            messageParser,
            createReportCalculationUseCase,
            reportCalculationChangeUseCase,
            identifyAffectedReportsUseCase,
        )
}
