package com.aamdigital.aambackendservice.reporting.report.queue

import com.aamdigital.aambackendservice.common.changes.domain.DocumentChangeEvent
import com.aamdigital.aambackendservice.common.domain.DomainReference
import com.aamdigital.aambackendservice.common.error.AamException
import com.aamdigital.aambackendservice.common.queue.core.QueueMessageParser
import com.aamdigital.aambackendservice.reporting.report.core.IdentifyAffectedReportsUseCase
import com.aamdigital.aambackendservice.reporting.report.di.ReportQueueConfiguration
import com.aamdigital.aambackendservice.reporting.reportcalculation.core.CreateReportCalculationRequest
import com.aamdigital.aambackendservice.reporting.reportcalculation.core.CreateReportCalculationUseCase
import com.aamdigital.aambackendservice.reporting.reportcalculation.core.ReportCalculationChangeUseCase
import com.aamdigital.aambackendservice.reporting.webhook.storage.WebhookStorage
import com.rabbitmq.client.Channel
import org.slf4j.LoggerFactory
import org.springframework.amqp.AmqpRejectAndDontRequeueException
import org.springframework.amqp.core.Message
import org.springframework.amqp.rabbit.annotation.RabbitListener

class ReportDocumentChangeEventConsumer(
    private val messageParser: QueueMessageParser,
    private val createReportCalculationUseCase: CreateReportCalculationUseCase,
    private val reportCalculationChangeUseCase: ReportCalculationChangeUseCase,
    private val identifyAffectedReportsUseCase: IdentifyAffectedReportsUseCase,
    private val webhookStorage: WebhookStorage,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @RabbitListener(
        queues = [ReportQueueConfiguration.Companion.DOCUMENT_CHANGES_REPORT_QUEUE],
        // avoid concurrent processing so that we do not trigger multiple calculations for same data unnecessarily
        concurrency = "1-1",
    )
    fun consume(rawMessage: String, message: Message, channel: Channel) {
        val type = try {
            messageParser.getTypeKClass(rawMessage.toByteArray())
        } catch (ex: AamException) {
            throw AmqpRejectAndDontRequeueException("[${ex.code}] ${ex.localizedMessage}", ex)
        }

        when (type.qualifiedName) {
            DocumentChangeEvent::class.qualifiedName -> {
                val payload: DocumentChangeEvent = messageParser.getPayload(
                    body = rawMessage.toByteArray(),
                    kClass = DocumentChangeEvent::class
                )

                if (payload.documentId.startsWith("ReportCalculation:")) {
                    if (payload.deleted) {
                        return
                    }
                    reportCalculationChangeUseCase.handle(
                        documentChangeEvent = payload
                    )
                    return
                }

                val affectedReports = identifyAffectedReportsUseCase.analyse(
                    documentChangeEvent = payload
                )

                val webhooks = webhookStorage.fetchAllWebhooks()

                affectedReports
                    .filter { report ->
                        // we only need to do automatic calculations for reports that are subscribed to
                        webhooks.any { webhook ->
                            webhook.reportSubscriptions.contains(DomainReference(report.id))
                        }
                    }
                    .forEach { report ->
                    createReportCalculationUseCase
                        .createReportCalculation(
                            request = CreateReportCalculationRequest(
                                report = report,
                                args = mutableMapOf(),
                                fromAutomaticChangeDetection = true,
                            )
                        )
                }

                return
            }

            else -> {
                logger.warn(
                    "Could not find any use case for this EventType: {}",
                    type.qualifiedName,
                )
                throw AmqpRejectAndDontRequeueException(
                    "[NO_USECASE_CONFIGURED] Could not find matching use case for: ${type.qualifiedName}",
                )
            }
        }
    }
}
