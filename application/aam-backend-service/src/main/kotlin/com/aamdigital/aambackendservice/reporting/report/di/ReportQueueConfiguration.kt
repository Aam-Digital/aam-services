package com.aamdigital.aambackendservice.reporting.report.di

import com.aamdigital.aambackendservice.common.queue.core.QueueMessageParser
import com.aamdigital.aambackendservice.reporting.report.core.IdentifyAffectedReportsUseCase
import com.aamdigital.aambackendservice.reporting.report.queue.ReportDocumentChangeEventConsumer
import com.aamdigital.aambackendservice.reporting.reportcalculation.core.CreateReportCalculationUseCase
import com.aamdigital.aambackendservice.reporting.reportcalculation.core.ReportCalculationChangeUseCase
import com.aamdigital.aambackendservice.reporting.webhook.storage.WebhookStorage
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

    @Bean("report-document-changes-exchange")
    fun reportDocumentChangesBinding(
        @Qualifier("report-config-changes-queue") queue: Queue,
        @Qualifier("document-changes-exchange") exchange: FanoutExchange,
    ): Binding = BindingBuilder.bind(queue).to(exchange)

    @Bean("report-document-changes-consumer")
    fun reportDocumentChangeEventConsumer(
        messageParser: QueueMessageParser,
        createReportCalculationUseCase: CreateReportCalculationUseCase,
        reportCalculationChangeUseCase: ReportCalculationChangeUseCase,
        identifyAffectedReportsUseCase: IdentifyAffectedReportsUseCase,
        webhookStorage: WebhookStorage,
    ): ReportDocumentChangeEventConsumer =
        ReportDocumentChangeEventConsumer(
            messageParser,
            createReportCalculationUseCase,
            reportCalculationChangeUseCase,
            identifyAffectedReportsUseCase,
            webhookStorage,
        )
}
