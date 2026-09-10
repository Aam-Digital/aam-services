package com.aamdigital.aambackendservice.reporting.reportcalculation.core

import com.aamdigital.aambackendservice.common.domain.UseCaseOutcome
import com.aamdigital.aambackendservice.reporting.reportcalculation.usecase.DefaultReportCalculationUseCase
import com.fasterxml.jackson.databind.ObjectMapper
import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationRegistry
import org.slf4j.LoggerFactory
import org.springframework.core.NestedExceptionUtils
import java.time.Duration

/**
 * Executes one stored report calculation and, when it produced a new result, notifies the webhooks
 * subscribed to that report.
 *
 * Called on the report calculation executor (see [ExecutorReportCalculationTrigger]), so it must
 * never let an exception escape: there is no caller to handle it, and the executor's thread would
 * only hand it to the default handler. A failed calculation is already recorded on the calculation
 * document as `FINISHED_ERROR`, which is what the API serves.
 *
 * The webhook notification sits in its own try/catch with a bounded retry, because the calculation
 * is complete and persisted by then - failing to notify must not re-run it or re-status it. Three
 * attempts and then give up is the same disposition as before, when the failure was retried by the
 * listener retry policy and then dead-lettered to a queue nothing has ever drained.
 */
class ReportCalculationProcessor(
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

    fun process(reportCalculationId: String) {
        val observation = Observation.createNotStarted("report-calculation-use-case", this.observationRegistry)
        observation.lowCardinalityKeyValue("reportCalculationId", reportCalculationId)
        observation.observe {
            try {
                runCalculation(reportCalculationId)
            } catch (ex: Exception) {
                val rootCause = NestedExceptionUtils.getMostSpecificCause(ex)
                logger.error(
                    "Report calculation {} failed unexpectedly: {}",
                    reportCalculationId,
                    rootCause.message,
                    rootCause
                )
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
                logger.error(
                    "Report calculation {} failed: [{}] {}",
                    reportCalculationId,
                    response.errorCode,
                    response.errorMessage,
                    response.cause
                )
            }

            is UseCaseOutcome.Success -> {
                logger.trace(objectMapper.writeValueAsString(response))
                notifyCompletion(reportCalculationId)
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
                    // ERROR so this is reported once to Sentry, grouped by its real cause
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
