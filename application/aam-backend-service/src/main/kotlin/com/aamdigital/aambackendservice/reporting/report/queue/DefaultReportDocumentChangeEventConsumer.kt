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
import com.rabbitmq.client.Channel
import org.slf4j.LoggerFactory
import org.springframework.amqp.AmqpRejectAndDontRequeueException
import org.springframework.amqp.core.Message
import org.springframework.amqp.rabbit.annotation.RabbitListener

class DefaultReportDocumentChangeEventConsumer(
    private val messageParser: QueueMessageParser,
    private val createReportCalculationUseCase: CreateReportCalculationUseCase,
    private val reportCalculationChangeUseCase: ReportCalculationChangeUseCase,
    private val identifyAffectedReportsUseCase: IdentifyAffectedReportsUseCase,
) : ReportDocumentChangeEventConsumer {

    private val logger = LoggerFactory.getLogger(javaClass)

    @RabbitListener(
        queues = [ReportQueueConfiguration.DOCUMENT_CHANGES_REPORT_QUEUE],
        // avoid concurrent processing so that we do not trigger multiple calculations for same data unnecessarily
        concurrency = "1-1",
    )
    override fun consume(rawMessage: String, message: Message, channel: Channel) {
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

                if (payload.documentId.startsWith("ReportConfig:")) {
                    logger.trace(payload.toString())

                    if (payload.deleted) {
                        logger.trace("Skipping ReportConfig delete event")
                        return
                    }

                    // todo if aggregationDefinition is different, skip trigger ReportCalculation

                    val reportRef = try {
                        payload.currentVersion["_id"] as String
                    } catch (ex: Exception) {
                        logger.warn(ex.message, ex)
                        return
                    }

                    createReportCalculationUseCase.createReportCalculation(
                        request = CreateReportCalculationRequest(
                            report = DomainReference(reportRef),
                            args = mutableMapOf()
                        )
                    )

                    return
                }

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

                affectedReports.forEach { report ->
                    createReportCalculationUseCase
                        .createReportCalculation(
                            request = CreateReportCalculationRequest(
                                report = report,
                                args = mutableMapOf()
                            )
                        )
                }

                return
            }

            else -> {
                logger.warn(
                    "[DefaultReportDocumentChangeEventConsumer] Could not find any use case for this EventType: {}",
                    type.qualifiedName,
                )
                throw AmqpRejectAndDontRequeueException(
                    "[NO_USECASE_CONFIGURED] Could not find matching use case for: ${type.qualifiedName}",
                )
            }
        }
    }
}
