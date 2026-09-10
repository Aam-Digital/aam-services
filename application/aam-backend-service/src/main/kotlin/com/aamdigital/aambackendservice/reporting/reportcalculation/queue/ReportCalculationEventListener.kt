package com.aamdigital.aambackendservice.reporting.reportcalculation.queue

import com.aamdigital.aambackendservice.common.domain.UseCaseOutcome
import com.aamdigital.aambackendservice.reporting.reportcalculation.ReportCalculationEvent
import com.aamdigital.aambackendservice.reporting.reportcalculation.core.ReportCalculationChangeUseCase
import com.aamdigital.aambackendservice.reporting.reportcalculation.core.ReportCalculationRequest
import com.aamdigital.aambackendservice.reporting.reportcalculation.di.ReportCalculationQueueConfiguration.Companion.REPORT_CALCULATION_EVENT_QUEUE
import com.aamdigital.aambackendservice.reporting.reportcalculation.usecase.DefaultReportCalculationUseCase
import com.fasterxml.jackson.databind.ObjectMapper
import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationRegistry
import org.slf4j.LoggerFactory
import org.springframework.amqp.AmqpRejectAndDontRequeueException
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.core.NestedExceptionUtils
import java.time.Duration

/**
 * Process ReportCalculationEvents from RabbitMQ.
 * When the reporting module is enabled, a RabbitListener is registered which handles incoming events.
 *
 * Once a calculation has finished successfully and been stored, the webhook-notification path is
 * started by calling [ReportCalculationChangeUseCase] directly. That call sits in its own
 * try/catch: the calculation is already complete and persisted at that point, so a failure to
 * notify must never re-run it, re-status it, or reject the message.
 *
 * Notification is attempted [completionRetryAttempts] times with an exponentially growing pause and
 * then given up on with an ERROR log. That is the same disposition as before, when the failure was
 * retried by the listener retry policy and then dead-lettered to
 * `report.calculation.completed.deadLetter` - a queue nothing has ever drained.
 *
 * The pause is deliberately shorter than the broker's 10s x 2.0 was: it now occupies one of this
 * queue's 2-5 consumers rather than a dedicated consumer of its own, and the failures reachable
 * from here are CouchDB reads, for which a few seconds is an adequate transient window.
 */
class ReportCalculationEventListener(
    val observationRegistry: ObservationRegistry,
    val reportCalculationUseCase: DefaultReportCalculationUseCase,
    val objectMapper: ObjectMapper,
    val reportCalculationChangeUseCase: ReportCalculationChangeUseCase,
    private val completionRetryAttempts: Int,
    private val completionRetryInitialInterval: Duration
) {
    companion object {
        private const val COMPLETION_RETRY_MULTIPLIER = 2L
    }

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

                is UseCaseOutcome.Success -> {
                    logger.trace(objectMapper.writeValueAsString(response))
                    notifyCompletion(event.reportCalculationId)
                }
            }
        }
    }

    private fun notifyCompletion(reportCalculationId: String) {
        val observation =
            Observation.createNotStarted("report-calculation-completed-use-case", observationRegistry)
        observation.lowCardinalityKeyValue("reportCalculationId", reportCalculationId)
        observation.observe {
            handleCompletionWithRetry(reportCalculationId)
        }
    }

    private fun handleCompletionWithRetry(reportCalculationId: String) {
        var interval = completionRetryInitialInterval

        for (attempt in 1..completionRetryAttempts) {
            try {
                reportCalculationChangeUseCase.handle(reportCalculationId)
                return
            } catch (ex: Exception) {
                if (attempt >= completionRetryAttempts) {
                    // ERROR so this is reported once to Sentry, grouped by its real cause - the same
                    // visibility QueueErrorHandler gave this failure while it was a queue hop
                    val rootCause = NestedExceptionUtils.getMostSpecificCause(ex)
                    logger.error(
                        "Giving up notifying webhook subscribers of completed report calculation {} " +
                            "after {} attempts: {}",
                        reportCalculationId,
                        completionRetryAttempts,
                        rootCause.message,
                        rootCause
                    )
                    return
                }

                logger.warn(
                    "Could not notify webhook subscribers of completed report calculation {} " +
                        "(attempt {} of {}), retrying in {}ms: {}",
                    reportCalculationId,
                    attempt,
                    completionRetryAttempts,
                    interval.toMillis(),
                    ex.localizedMessage
                )

                sleepBeforeRetry(interval)
                interval = interval.multipliedBy(COMPLETION_RETRY_MULTIPLIER)
            }
        }
    }

    private fun sleepBeforeRetry(interval: Duration) {
        if (interval.isZero || interval.isNegative) {
            return
        }

        try {
            Thread.sleep(interval.toMillis())
        } catch (ex: InterruptedException) {
            Thread.currentThread().interrupt()
            logger.debug("interrupted while waiting to retry completion notification", ex)
        }
    }
}
