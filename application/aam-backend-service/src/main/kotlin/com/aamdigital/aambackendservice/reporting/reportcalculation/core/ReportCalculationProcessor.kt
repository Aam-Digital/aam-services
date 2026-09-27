package com.aamdigital.aambackendservice.reporting.reportcalculation.core

import com.aamdigital.aambackendservice.common.domain.UseCaseOutcome
import com.aamdigital.aambackendservice.common.error.InvalidArgumentException
import com.aamdigital.aambackendservice.common.execution.InlineRetry
import com.aamdigital.aambackendservice.reporting.reportcalculation.usecase.DefaultReportCalculationUseCase
import com.fasterxml.jackson.databind.ObjectMapper
import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationRegistry
import org.slf4j.LoggerFactory
import org.springframework.core.NestedExceptionUtils

/**
 * Executes one stored report calculation and, when it produced a new result, notifies the webhooks
 * subscribed to that report.
 *
 * Called off the caller's thread (see [ExecutorReportCalculationTrigger]). A failed calculation is
 * already recorded on the calculation document as `FINISHED_ERROR`, which is what the API serves,
 * so this only has to log it.
 *
 * A calculation that failed on invalid input (an [InvalidArgumentException] anywhere in the cause
 * chain, e.g. a ReportConfig whose query SQS rejects) is logged at INFO rather than ERROR: it is a
 * configuration problem of the individual instance, not a backend defect, so it must not raise
 * Sentry alerts. That is why every exception is caught here rather than left to the task runner,
 * which would log it at ERROR.
 *
 * The webhook notification gets its own bounded [InlineRetry], because the calculation is complete
 * and persisted by then - failing to notify must not re-run it or re-status it. Three attempts and
 * then give up is the same disposition as before, when the failure was retried by the listener
 * retry policy and then dead-lettered to a queue nothing has ever drained.
 */
class ReportCalculationProcessor(
    val observationRegistry: ObservationRegistry,
    val reportCalculationUseCase: DefaultReportCalculationUseCase,
    val objectMapper: ObjectMapper,
    val reportCalculationChangeUseCase: ReportCalculationChangeUseCase,
    private val completionRetry: InlineRetry
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun process(reportCalculationId: String) {
        val observation = Observation.createNotStarted("report-calculation-use-case", this.observationRegistry)
        observation.lowCardinalityKeyValue("reportCalculationId", reportCalculationId)
        observation.observe {
            try {
                runCalculation(reportCalculationId)
            } catch (ex: Exception) {
                val rootCause = NestedExceptionUtils.getMostSpecificCause(ex)
                if (isInvalidInput(ex)) {
                    logger.info(
                        "Report calculation {} rejected invalid input: {}",
                        reportCalculationId,
                        rootCause.message,
                        rootCause
                    )
                } else {
                    logger.error(
                        "Report calculation {} failed unexpectedly: {}",
                        reportCalculationId,
                        rootCause.message,
                        rootCause
                    )
                }
            }
        }
    }

    private fun runCalculation(reportCalculationId: String) {
        val response =
            reportCalculationUseCase.run(
                request = ReportCalculationRequest(reportCalculationId = reportCalculationId)
            )

        when (response) {
            is UseCaseOutcome.Failure -> {
                if (isInvalidInput(response.cause)) {
                    logger.info(
                        "Report calculation {} rejected invalid input: [{}] {}",
                        reportCalculationId,
                        response.errorCode,
                        response.errorMessage,
                        response.cause
                    )
                } else {
                    logger.error(
                        "Report calculation {} failed: [{}] {}",
                        reportCalculationId,
                        response.errorCode,
                        response.errorMessage,
                        response.cause
                    )
                }
            }

            is UseCaseOutcome.Success -> {
                logger.trace(objectMapper.writeValueAsString(response))
                notifyCompletion(reportCalculationId)
            }
        }
    }

    private fun isInvalidInput(t: Throwable?): Boolean =
        generateSequence(t) { it.cause.takeIf { cause -> cause !== it } }
            .any { it is InvalidArgumentException }

    private fun notifyCompletion(reportCalculationId: String) {
        val observation =
            Observation.createNotStarted("report-calculation-completed-use-case", observationRegistry)
        observation.lowCardinalityKeyValue("reportCalculationId", reportCalculationId)
        observation.observe {
            completionRetry.run(
                "notifying webhook subscribers of completed report calculation $reportCalculationId"
            ) {
                reportCalculationChangeUseCase.handle(reportCalculationId)
            }
        }
    }
}
