package com.aamdigital.aambackendservice.reporting.reportcalculation.di

import com.aamdigital.aambackendservice.common.couchdb.core.CouchDbClient
import com.aamdigital.aambackendservice.common.couchdb.core.DatabaseRequest
import com.aamdigital.aambackendservice.common.domain.FileStorage
import com.aamdigital.aambackendservice.common.execution.BoundedTaskRunner
import com.aamdigital.aambackendservice.common.execution.InlineRetry
import com.aamdigital.aambackendservice.reporting.ConditionalOnReportingEnabled
import com.aamdigital.aambackendservice.reporting.report.core.QueryStorage
import com.aamdigital.aambackendservice.reporting.report.core.ReportStorage
import com.aamdigital.aambackendservice.reporting.reportcalculation.core.CreateReportCalculationUseCase
import com.aamdigital.aambackendservice.reporting.reportcalculation.core.ExecutorReportCalculationTrigger
import com.aamdigital.aambackendservice.reporting.reportcalculation.core.ReportCalculationChangeUseCase
import com.aamdigital.aambackendservice.reporting.reportcalculation.core.ReportCalculationDebouncer
import com.aamdigital.aambackendservice.reporting.reportcalculation.core.ReportCalculationProcessor
import com.aamdigital.aambackendservice.reporting.reportcalculation.core.ReportCalculationStorage
import com.aamdigital.aambackendservice.reporting.reportcalculation.core.ReportCalculationSweeper
import com.aamdigital.aambackendservice.reporting.reportcalculation.core.ReportCalculationTrigger
import com.aamdigital.aambackendservice.reporting.reportcalculation.storage.DefaultReportCalculationStorage
import com.aamdigital.aambackendservice.reporting.reportcalculation.usecase.DefaultCreateReportCalculationUseCase
import com.aamdigital.aambackendservice.reporting.reportcalculation.usecase.DefaultReportCalculationChangeUseCase
import com.aamdigital.aambackendservice.reporting.reportcalculation.usecase.DefaultReportCalculationUseCase
import com.aamdigital.aambackendservice.reporting.transformation.DataTransformation
import com.aamdigital.aambackendservice.reporting.transformation.SqlFromDateTransformation
import com.aamdigital.aambackendservice.reporting.transformation.SqlToDateTransformation
import com.aamdigital.aambackendservice.reporting.webhook.core.WebhookTriggerService
import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.databind.ObjectMapper
import io.micrometer.observation.ObservationRegistry
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration
import java.util.concurrent.Executor

@Configuration
@ConditionalOnReportingEnabled
class ReportCalculationConfiguration {
    companion object {
        /**
         * A calculation holds an SQS query open for seconds to minutes and SQS is effectively
         * single-threaded, so only a few may run at once. The `report.calculation` queue's
         * consumers scaled between 2 and 5 under load; this runs 2 at a time.
         */
        private const val CALCULATION_CONCURRENCY = 2
        private const val CALCULATION_BACKLOG = 500
        private val CALCULATION_SHUTDOWN_TIMEOUT: Duration = Duration.ofSeconds(60)
    }

    /**
     * Runs report calculations off the thread that requested them.
     *
     * On shutdown, queued and in-flight calculations get [CALCULATION_SHUTDOWN_TIMEOUT] to finish;
     * anything still queued stays `PENDING` and is picked up by [ReportCalculationSweeper] after the
     * restart.
     */
    @Bean("report-calculation-executor")
    fun reportCalculationExecutor(): Executor =
        BoundedTaskRunner.threadPool(
            name = "report-calculation",
            concurrency = CALCULATION_CONCURRENCY,
            backlog = CALCULATION_BACKLOG,
            shutdownTimeout = CALCULATION_SHUTDOWN_TIMEOUT
        )

    @Bean
    fun reportCalculationProcessor(
        observationRegistry: ObservationRegistry,
        reportCalculationUseCase: DefaultReportCalculationUseCase,
        reportCalculationChangeUseCase: ReportCalculationChangeUseCase,
        @Value("\${report-calculation-completion.retry-attempts:3}") completionRetryAttempts: Int,
        @Value("\${report-calculation-completion.retry-initial-interval-millis:1000}")
        completionRetryInitialIntervalMillis: Long
    ): ReportCalculationProcessor =
        ReportCalculationProcessor(
            observationRegistry = observationRegistry,
            reportCalculationUseCase = reportCalculationUseCase,
            reportCalculationChangeUseCase = reportCalculationChangeUseCase,
            completionRetry =
                InlineRetry(
                    attempts = completionRetryAttempts,
                    initialInterval = Duration.ofMillis(completionRetryInitialIntervalMillis)
                )
        )

    @Bean
    fun reportCalculationTrigger(
        @Qualifier("report-calculation-executor") reportCalculationExecutor: Executor,
        reportCalculationProcessor: ReportCalculationProcessor
    ): ReportCalculationTrigger =
        ExecutorReportCalculationTrigger(
            reportCalculationRunner = BoundedTaskRunner("report-calculation", reportCalculationExecutor),
            reportCalculationProcessor = reportCalculationProcessor
        )

    @Bean
    fun reportCalculationSweeper(
        reportCalculationStorage: ReportCalculationStorage,
        reportCalculationTrigger: ReportCalculationTrigger
    ): ReportCalculationSweeper =
        ReportCalculationSweeper(
            reportCalculationStorage = reportCalculationStorage,
            reportCalculationTrigger = reportCalculationTrigger
        )

    @Bean("report-calculation-database-request")
    fun reportCalculationDatabaseRequest(): DatabaseRequest = DatabaseRequest("report-calculation")

    @Bean("notification-webhook-database-request")
    fun notificationWebhookDatabaseRequest(): DatabaseRequest = DatabaseRequest("notification-webhook")

    @Bean
    fun defaultReportCalculationStorage(
        couchDbClient: CouchDbClient,
        fileStorage: FileStorage
    ): ReportCalculationStorage = DefaultReportCalculationStorage(couchDbClient, fileStorage)

    @Bean
    fun defaultReportCalculationChangeUseCase(
        reportCalculationStorage: ReportCalculationStorage,
        webhookTriggerService: WebhookTriggerService
    ): ReportCalculationChangeUseCase =
        DefaultReportCalculationChangeUseCase(reportCalculationStorage, webhookTriggerService)

    @Bean
    fun defaultCreateReportCalculationUseCase(
        reportCalculationStorage: ReportCalculationStorage,
        reportCalculationTrigger: ReportCalculationTrigger
    ) = DefaultCreateReportCalculationUseCase(reportCalculationStorage, reportCalculationTrigger)

    @Bean
    fun reportCalculationDebouncer(
        createReportCalculationUseCase: CreateReportCalculationUseCase,
        @Value("\${report-calculation-debounce.quiet-period-seconds:60}") quietPeriodSeconds: Long,
        @Value("\${report-calculation-debounce.max-wait-seconds:300}") maxWaitSeconds: Long,
    ): ReportCalculationDebouncer =
        ReportCalculationDebouncer(
            createReportCalculationUseCase = createReportCalculationUseCase,
            quietPeriod = Duration.ofSeconds(quietPeriodSeconds),
            maxWait = Duration.ofSeconds(maxWaitSeconds),
        )

    @Bean
    fun getSqlFromDateTransformation(): DataTransformation<String> = SqlFromDateTransformation()

    @Bean
    fun getSqlToDateTransformation(): DataTransformation<String> = SqlToDateTransformation()

    @Bean
    fun getJsonFactory(objectMapper: ObjectMapper): JsonFactory = JsonFactory().setCodec(objectMapper)

    @Bean
    fun defaultReportCalculationUseCase(
        reportCalculationStorage: ReportCalculationStorage,
        reportStorage: ReportStorage,
        transformations: List<DataTransformation<String>>,
        queryStorage: QueryStorage
    ): DefaultReportCalculationUseCase =
        DefaultReportCalculationUseCase(
            reportCalculationStorage,
            reportStorage,
            transformations,
            queryStorage
        )
}
