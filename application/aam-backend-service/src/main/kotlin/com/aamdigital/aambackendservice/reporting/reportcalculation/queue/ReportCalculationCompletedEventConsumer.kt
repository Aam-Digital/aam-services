package com.aamdigital.aambackendservice.reporting.reportcalculation.queue

import com.aamdigital.aambackendservice.reporting.reportcalculation.ReportCalculationCompletedEvent
import com.aamdigital.aambackendservice.reporting.reportcalculation.core.ReportCalculationChangeUseCase
import com.aamdigital.aambackendservice.reporting.reportcalculation.di.ReportCalculationQueueConfiguration.Companion.REPORT_CALCULATION_COMPLETED_QUEUE
import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationRegistry
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.annotation.RabbitListener

/**
 * Processes [ReportCalculationCompletedEvent]s from RabbitMQ.
 *
 * [ReportCalculationEventListener] publishes an explicit completion event,
 * which this consumer hands to the [ReportCalculationChangeUseCase] to trigger the subscribed webhooks.
 *
 * Exceptions are intentionally left to propagate: a transient failure (e.g. CouchDB hiccup) is then
 * retried by the listener retry policy before the message is dropped, rather than being swallowed.
 */
class ReportCalculationCompletedEventConsumer(
    private val observationRegistry: ObservationRegistry,
    private val reportCalculationChangeUseCase: ReportCalculationChangeUseCase
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    init {
        logger.debug(
            "[ReportCalculationCompletedEventConsumer] Initiate RabbitListener " +
                "for Queue '$REPORT_CALCULATION_COMPLETED_QUEUE'"
        )
    }

    @RabbitListener(
        queues = [REPORT_CALCULATION_COMPLETED_QUEUE]
    )
    fun consume(event: ReportCalculationCompletedEvent) {
        val observation =
            Observation.createNotStarted("report-calculation-completed-use-case", observationRegistry)
        observation.lowCardinalityKeyValue("reportCalculationId", event.reportCalculationId)
        observation.observe {
            reportCalculationChangeUseCase.handle(event.reportCalculationId)
        }
    }
}
