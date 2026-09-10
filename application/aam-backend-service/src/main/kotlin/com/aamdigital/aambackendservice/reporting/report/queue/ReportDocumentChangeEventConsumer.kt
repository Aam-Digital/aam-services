package com.aamdigital.aambackendservice.reporting.report.queue

import com.aamdigital.aambackendservice.common.changes.DocumentChangeEvent
import com.aamdigital.aambackendservice.common.error.AamException
import com.aamdigital.aambackendservice.common.queue.core.QueueMessageParser
import com.aamdigital.aambackendservice.reporting.report.core.IdentifyAffectedReportsUseCase
import com.aamdigital.aambackendservice.reporting.report.di.ReportQueueConfiguration
import com.aamdigital.aambackendservice.reporting.reportcalculation.core.CreateReportCalculationRequest
import com.aamdigital.aambackendservice.reporting.reportcalculation.core.ReportCalculationDebouncer
import com.aamdigital.aambackendservice.reporting.webhook.storage.WebhookSubscriptionCache
import com.rabbitmq.client.Channel
import org.slf4j.LoggerFactory
import org.springframework.amqp.AmqpRejectAndDontRequeueException
import org.springframework.amqp.core.Message
import org.springframework.amqp.rabbit.annotation.RabbitListener

class ReportDocumentChangeEventConsumer(
    private val messageParser: QueueMessageParser,
    private val reportCalculationDebouncer: ReportCalculationDebouncer,
    private val identifyAffectedReportsUseCase: IdentifyAffectedReportsUseCase,
    private val webhookSubscriptionCache: WebhookSubscriptionCache
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @RabbitListener(
        queues = [ReportQueueConfiguration.Companion.DOCUMENT_CHANGES_REPORT_QUEUE],
        // avoid concurrent processing so that we do not trigger multiple calculations for same data unnecessarily
        concurrency = "1-1"
    )
    fun consume(
        rawMessage: String,
        message: Message,
        channel: Channel
    ) {
        val type =
            try {
                messageParser.getTypeKClass(rawMessage.toByteArray())
            } catch (ex: AamException) {
                throw AmqpRejectAndDontRequeueException("[${ex.code}] ${ex.localizedMessage}", ex)
            }

        when (type.qualifiedName) {
            DocumentChangeEvent::class.qualifiedName -> {
                val payload: DocumentChangeEvent =
                    messageParser.getPayload(
                        body = rawMessage.toByteArray(),
                        kClass = DocumentChangeEvent::class
                    )

                val affectedReports =
                    identifyAffectedReportsUseCase.analyse(
                        documentChangeEvent = payload
                    )

                if (affectedReports.isEmpty()) {
                    return
                }

                val subscribedReportIds = webhookSubscriptionCache.subscribedReportIds()

                affectedReports
                    .filter { report ->
                        // we only need to do automatic calculations for reports that are subscribed to
                        subscribedReportIds.contains(report.id)
                    }.forEach { report ->
                        // debounced: the calculation is only created once changes settle down,
                        // so bursts of document changes result in a single recalculation
                        reportCalculationDebouncer.recordChange(
                            request =
                                CreateReportCalculationRequest(
                                    report = report,
                                    args = mutableMapOf(),
                                    fromAutomaticChangeDetection = true
                                )
                        )
                    }

                return
            }

            else -> {
                logger.warn(
                    "Could not find any use case for this EventType: {}",
                    type.qualifiedName
                )
                throw AmqpRejectAndDontRequeueException(
                    "[NO_USECASE_CONFIGURED] Could not find matching use case for: ${type.qualifiedName}"
                )
            }
        }
    }
}
