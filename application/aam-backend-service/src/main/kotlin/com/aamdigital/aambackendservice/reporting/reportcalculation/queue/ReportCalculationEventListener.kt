package com.aamdigital.aambackendservice.reporting.reportcalculation.queue

import com.aamdigital.aambackendservice.common.domain.UseCaseOutcome
import com.aamdigital.aambackendservice.reporting.reportcalculation.ReportCalculationEvent
import com.aamdigital.aambackendservice.reporting.reportcalculation.core.ReportCalculationRequest
import com.aamdigital.aambackendservice.reporting.reportcalculation.di.ReportCalculationQueueConfiguration.Companion.REPORT_CALCULATION_EVENT_QUEUE
import com.aamdigital.aambackendservice.reporting.reportcalculation.usecase.DefaultReportCalculationUseCase
import com.fasterxml.jackson.databind.ObjectMapper
import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationRegistry
import org.slf4j.LoggerFactory
import org.springframework.amqp.AmqpRejectAndDontRequeueException
import org.springframework.amqp.rabbit.annotation.RabbitListener

/**
 * Process ReportCalculationEvents from RabbitMQ.
 * When the reporting module is enabled, a RabbitListener is registered which handles incoming events.
 */
class ReportCalculationEventListener(
    val observationRegistry: ObservationRegistry,
    val reportCalculationUseCase: DefaultReportCalculationUseCase,
    val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    init {
        logger.debug(
            "[ReportCalculationEventListener] Initiate RabbitListener " +
                "for Queue '$REPORT_CALCULATION_EVENT_QUEUE'"
        )
    }

    @RabbitListener(
        queues = [REPORT_CALCULATION_EVENT_QUEUE],
        concurrency = "2-5"
    )
    fun handleReportCalculationEvent(event: ReportCalculationEvent) {
        val observation = Observation.createNotStarted("report-calculation-use-case", this.observationRegistry)
        observation.lowCardinalityKeyValue("reportCalculationId", event.reportCalculationId)
//        observation.lowCardinalityKeyValue("realm", event.tenant) // prepare tenant support
        observation.observe {
            val response =
                reportCalculationUseCase.run(
                    request =
                        ReportCalculationRequest(
                            reportCalculationId = event.reportCalculationId
                        )
                )

            when (response) {
                is UseCaseOutcome.Failure -> throw AmqpRejectAndDontRequeueException(
                    "[${response.errorCode}] ${response.errorMessage}",
                    response.cause
                )

                is UseCaseOutcome.Success -> logger.trace(objectMapper.writeValueAsString(response))
            }
        }
    }
}
