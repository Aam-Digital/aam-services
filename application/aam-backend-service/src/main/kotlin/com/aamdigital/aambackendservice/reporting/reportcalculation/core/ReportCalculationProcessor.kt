package com.aamdigital.aambackendservice.reporting.reportcalculation.core

import com.aamdigital.aambackendservice.common.domain.UseCaseOutcome
import com.aamdigital.aambackendservice.common.error.InvalidArgumentException
import com.aamdigital.aambackendservice.common.execution.InlineRetry
import com.aamdigital.aambackendservice.reporting.reportcalculation.usecase.DefaultReportCalculationUseCase
import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationRegistry
import org.slf4j.LoggerFactory

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
 * Sentry alerts.
 *
 * The webhook notification gets its own bounded [InlineRetry], because the calculation is complete
 * and persisted by then - failing to notify must not re-run it or re-status it. Three attempts and
 * then give up is the same disposition as before, when the failure was retried by the listener
 * retry policy and then dead-lettered to a queue nothing has ever drained.
 */
class ReportCalculationProcessor(
    private val observationRegistry: ObservationRegistry,
    private val reportCalculationUseCase: DefaultReportCalculationUseCase,
    private val reportCalculationChangeUseCase: ReportCalculationChangeUseCase,
    private val completionRetry: InlineRetry
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun process(reportCalculationId: String) {
        val observation = Observation.createNotStarted("report-calculation-use-case", observationRegistry)
        observation.lowCardinalityKeyValue("reportCalculationId", reportCalculationId)
        observation.observe {
            val outcome =
                reportCalculationUseCase.run(
                    request = ReportCalculationRequest(reportCalculationId = reportCalculationId)
                )

            when (outcome) {
                is UseCaseOutcome.Failure -> logFailure(reportCalculationId, outcome)
                is UseCaseOutcome.Success -> notifyCompletion(reportCalculationId)
            }
        }
    }

    private fun logFailure(
        reportCalculationId: String,
        failure: UseCaseOutcome.Failure<ReportCalculationData>
    ) {
        val invalidInput =
            generateSequence(failure.cause) { it.cause.takeIf { cause -> cause !== it } }
                .any { it is InvalidArgumentException }

        if (invalidInput) {
            logger.info(
                "Report calculation {} rejected invalid input: [{}] {}",
                reportCalculationId,
                failure.errorCode,
                failure.errorMessage,
                failure.cause
            )
        } else {
            logger.error(
                "Report calculation {} failed: [{}] {}",
                reportCalculationId,
                failure.errorCode,
                failure.errorMessage,
                failure.cause
            )
        }
    }

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
