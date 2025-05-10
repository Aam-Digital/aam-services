package com.aamdigital.aambackendservice.reporting.reportcalculation.di

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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component


/**
 * Process ReportCalculationEvents from RabbitMQ
 * When enabled, an RabbitListener is registered which will handle incoming events.
 * When disabled, ReportCalculations will not be calculated.
 */
@Component
@ConditionalOnProperty(
    prefix = "events.listener.report-calculation",
    name = ["enabled"],
    havingValue = "true",
)
class ReportCalculationEventListener(
    val observationRegistry: ObservationRegistry,
    val reportCalculationUseCase: DefaultReportCalculationUseCase,
    val objectMapper: ObjectMapper,
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
        concurrency = "2-5",
    )
    fun handleReportCalculationEvent(
        event: ReportCalculationEvent,
    ) {
        val observation = Observation.createNotStarted("report-calculation-use-case", this.observationRegistry)
        observation.lowCardinalityKeyValue("reportCalculationId", event.reportCalculationId)
//        observation.lowCardinalityKeyValue("realm", event.tenant) // prepare tenant support
        observation.observe {
            val response = reportCalculationUseCase.run(
                request = ReportCalculationRequest(
                    reportCalculationId = event.reportCalculationId,
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
