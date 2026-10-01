package com.aamdigital.aambackendservice.reporting.reportcalculation.core

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.aamdigital.aambackendservice.common.domain.DomainReference
import com.aamdigital.aambackendservice.common.execution.InlineRetry
import com.aamdigital.aambackendservice.reporting.report.Report
import com.aamdigital.aambackendservice.reporting.report.ReportItem
import com.aamdigital.aambackendservice.reporting.report.core.ReportStorage
import com.aamdigital.aambackendservice.reporting.report.sqs.SqsQueryStorage
import com.aamdigital.aambackendservice.reporting.report.sqs.SqsSchemaService
import com.aamdigital.aambackendservice.reporting.reportcalculation.ReportCalculation
import com.aamdigital.aambackendservice.reporting.reportcalculation.ReportCalculationStatus
import com.aamdigital.aambackendservice.reporting.reportcalculation.usecase.DefaultReportCalculationUseCase
import io.micrometer.observation.ObservationRegistry
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.slf4j.LoggerFactory
import org.springframework.web.client.RestClient
import java.time.Duration

/**
 * Runs a report calculation whose query SQS rejects through the real [SqsQueryStorage],
 * [DefaultReportCalculationUseCase] and [ReportCalculationProcessor], so what reaches the logs is
 * checked for every logger on that path rather than for the processor alone.
 */
class RejectedReportQueryLoggingTest {
    companion object {
        private const val CALCULATION_ID = "ReportCalculation:1"
        private const val REPORT_ID = "ReportConfig:1"
        private const val SQS_EXPLANATION = "no such column: s.nickname"
    }

    private val reportCalculationStorage: ReportCalculationStorage = mock()
    private val reportStorage: ReportStorage = mock()
    private val schemaService: SqsSchemaService = mock()

    private lateinit var mockWebServer: MockWebServer
    private lateinit var processor: ReportCalculationProcessor
    private lateinit var rootLogger: Logger
    private lateinit var serviceLogger: Logger
    private var previousServiceLevel: Level? = null
    private lateinit var appender: ListAppender<ILoggingEvent>

    @BeforeEach
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        processor =
            ReportCalculationProcessor(
                observationRegistry = ObservationRegistry.create(),
                reportCalculationUseCase =
                    DefaultReportCalculationUseCase(
                        reportCalculationStorage = reportCalculationStorage,
                        reportStorage = reportStorage,
                        transformations = emptyList(),
                        queryStorage =
                            SqsQueryStorage(
                                RestClient.builder().baseUrl(mockWebServer.url("/").toString()).build(),
                                schemaService
                            )
                    ),
                reportCalculationChangeUseCase = mock(),
                completionRetry = InlineRetry(attempts = 1, initialInterval = Duration.ZERO)
            )
        rootLogger = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) as Logger
        appender = ListAppender<ILoggingEvent>().apply { start() }
        // the application.yaml default is WARN, and an earlier Spring test in the same JVM leaves it applied
        serviceLogger = LoggerFactory.getLogger("com.aamdigital.aambackendservice") as Logger
        previousServiceLevel = serviceLogger.level
        serviceLogger.level = Level.INFO
        rootLogger.addAppender(appender)
    }

    @AfterEach
    fun tearDown() {
        rootLogger.detachAppender(appender)
        serviceLogger.level = previousServiceLevel
        mockWebServer.shutdown()
    }

    @Test
    fun `should log a query that SQS rejects once at INFO, without SQS's explanation in the log line`() {
        // Given
        whenever(schemaService.getSchemaPath()).thenReturn("/app/_design/sqlite:config")
        whenever(reportCalculationStorage.fetchReportCalculation(DomainReference(CALCULATION_ID)))
            .thenReturn(
                ReportCalculation(
                    id = CALCULATION_ID,
                    report = DomainReference(REPORT_ID),
                    status = ReportCalculationStatus.PENDING
                )
            )
        whenever(reportStorage.fetchReport(DomainReference(REPORT_ID)))
            .thenReturn(
                Report(
                    id = REPORT_ID,
                    title = "Report",
                    items = listOf(ReportItem.ReportQuery(sql = "SELECT s.nickname FROM School s"))
                )
            )
        whenever(reportCalculationStorage.storeCalculation(any())).thenAnswer { it.arguments[0] }
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(400)
                .setBody("""{"statusCode":400,"error":"Bad Request","message":"$SQS_EXPLANATION"}""")
        )

        // When
        processor.process(CALCULATION_ID)

        // Then: only the events of this thread, so a background thread of another test cannot interfere
        val events = appender.list.filter { it.threadName == Thread.currentThread().name }
        assertThat(events.filter { it.level.isGreaterOrEqual(Level.WARN) }).isEmpty()
        val processorEvents = events.filter { it.loggerName == ReportCalculationProcessor::class.java.name }
        assertThat(processorEvents).hasSize(1)
        assertThat(processorEvents.first().level).isEqualTo(Level.INFO)
        assertThat(processorEvents.first().throwableProxy.message).contains(SQS_EXPLANATION)
        assertThat(events.map { it.formattedMessage }).noneMatch { it.contains(SQS_EXPLANATION) }
    }
}
