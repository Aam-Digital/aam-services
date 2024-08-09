package com.aamdigital.aambackendservice.reporting.report.core

import com.aamdigital.aambackendservice.domain.DomainReference
import com.aamdigital.aambackendservice.error.AamException
import com.aamdigital.aambackendservice.queue.core.QueueMessageParser
import com.aamdigital.aambackendservice.reporting.domain.event.DocumentChangeEvent
import com.aamdigital.aambackendservice.reporting.report.di.ReportQueueConfiguration
import com.aamdigital.aambackendservice.reporting.reportcalculation.core.CreateReportCalculationRequest
import com.aamdigital.aambackendservice.reporting.reportcalculation.core.CreateReportCalculationUseCase
import com.aamdigital.aambackendservice.reporting.reportcalculation.core.ReportCalculationChangeUseCase
import com.rabbitmq.client.Channel
import org.slf4j.LoggerFactory
import org.springframework.amqp.AmqpRejectAndDontRequeueException
import org.springframework.amqp.core.Message
import org.springframework.amqp.rabbit.annotation.RabbitListener
import reactor.core.publisher.Mono

class DefaultReportDocumentChangeEventConsumer(
    private val messageParser: QueueMessageParser,
    private val createReportCalculationUseCase: CreateReportCalculationUseCase,
    private val reportCalculationChangeUseCase: ReportCalculationChangeUseCase,
    private val identifyAffectedReportsUseCase: IdentifyAffectedReportsUseCase,
) : ReportDocumentChangeEventConsumer {

    private val logger = LoggerFactory.getLogger(javaClass)

    @RabbitListener(
        queues = [ReportQueueConfiguration.DOCUMENT_CHANGES_REPORT_QUEUE],
        ackMode = "MANUAL",
        concurrency = "1-1",
        batch = "1"
    )
    override fun consume(rawMessage: String, message: Message, channel: Channel): Mono<Unit> {
        val type = try {
            messageParser.getTypeKClass(rawMessage.toByteArray())
        } catch (ex: AamException) {
            return Mono.error { throw AmqpRejectAndDontRequeueException("[${ex.code}] ${ex.localizedMessage}", ex) }
        }

        when (type.qualifiedName) {
            DocumentChangeEvent::class.qualifiedName -> {
                val payload = messageParser.getPayload(
                    body = rawMessage.toByteArray(),
                    kClass = DocumentChangeEvent::class
                )

                if (payload.documentId.startsWith("ReportConfig:")) {
                    logger.info(payload.toString())

                    // todo if aggregationDefinition is different, skip trigger ReportCalculation

                    val reportRef = payload.currentVersion["_id"] as String

                    return createReportCalculationUseCase.createReportCalculation(
                        request = CreateReportCalculationRequest(
                            report = DomainReference(reportRef),
                            args = mutableMapOf()
                        )
                    ).flatMap { Mono.empty() }
                }

                if (payload.documentId.startsWith("ReportCalculation:")) {
                    if (payload.deleted) {
                        return Mono.empty()
                    }
                    return reportCalculationChangeUseCase.handle(
                        documentChangeEvent = payload
                    ).flatMap { Mono.empty() }
                }

                return identifyAffectedReportsUseCase.analyse(
                    documentChangeEvent = payload
                )
                    .flatMap { affectedReports ->
                        Mono.zip(affectedReports.map { report ->
                            createReportCalculationUseCase
                                .createReportCalculation(
                                    request = CreateReportCalculationRequest(
                                        report = report,
                                        args = mutableMapOf()
                                    )
                                )
                        }) {
                            it.iterator()
                        }
                    }
                    .flatMap { Mono.empty<Unit>() }
                    .doOnError {
                        logger.error(it.localizedMessage)
                    }

            }

            else -> {
                logger.warn(
                    "[DefaultReportDocumentChangeEventConsumer] Could not find any use case for this EventType: {}",
                    type.qualifiedName,
                )

                throw AmqpRejectAndDontRequeueException(
                    "[NO_USECASE_CONFIGURED] Could not found matching use case for: ${type.qualifiedName}",
                )
            }
        }
    }
}
