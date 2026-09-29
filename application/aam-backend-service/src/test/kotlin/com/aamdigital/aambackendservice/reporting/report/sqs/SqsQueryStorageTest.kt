package com.aamdigital.aambackendservice.reporting.report.sqs

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.aamdigital.aambackendservice.common.error.ExternalSystemException
import com.aamdigital.aambackendservice.common.error.InvalidArgumentException
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.slf4j.LoggerFactory
import org.springframework.web.client.RestClient

class SqsQueryStorageTest {
    companion object {
        // the shape of SQS error responses
        private const val INVALID_QUERY_RESPONSE =
            """{"statusCode":400,"error":"Bad Request","message":"no such column: foo"}"""

        // an error response can quote the tenant's query
        private const val QUERY_QUOTING_RESPONSE =
            """{"statusCode":500,"error":"Internal Server Error","message":"SELECT foo FROM bar"}"""
    }

    private lateinit var mockWebServer: MockWebServer
    private lateinit var storage: SqsQueryStorage
    private val schemaService: SqsSchemaService = mock()

    @BeforeEach
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        val restClient = RestClient.builder().baseUrl(mockWebServer.url("/").toString()).build()
        whenever(schemaService.getSchemaPath()).thenReturn("/_design/sqs/_view")
        storage = SqsQueryStorage(restClient, schemaService)
    }

    @AfterEach
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `should return the response stream on success`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("""[{"foo":1}]"""))

        val result = storage.executeQuery(QueryRequest("SELECT foo FROM bar", emptyList()), "Report:1")

        result.use { response ->
            assertThat(response.readBytes().decodeToString()).isEqualTo("""[{"foo":1}]""")
        }
    }

    @Test
    fun `should throw InvalidArgumentException carrying the SQS explanation and report id on 400 (invalid query)`() {
        // Given
        mockWebServer.enqueue(MockResponse().setResponseCode(400).setBody(INVALID_QUERY_RESPONSE))

        // When
        val thrown =
            catchThrowable { storage.executeQuery(QueryRequest("SELECT foo FROM bar", emptyList()), "Report:1") }

        // Then
        assertThat(thrown).isInstanceOf(InvalidArgumentException::class.java)
        assertThat((thrown as InvalidArgumentException).code)
            .isEqualTo(SqsQueryStorage.SqsQueryStorageErrorCode.QUERY_FAILED)
        assertThat(thrown.message).contains(INVALID_QUERY_RESPONSE)
        assertThat(thrown.message).contains("Report:1")
    }

    @ParameterizedTest
    @ValueSource(ints = [401, 403, 404, 500])
    fun `should treat any error status other than 400 as a failure of SQS rather than an invalid query`(status: Int) {
        // Given SQS fails the request for a reason the report author cannot fix, e.g. wrong
        // credentials, a missing design document or an error of SQS itself
        mockWebServer.enqueue(MockResponse().setResponseCode(status).setBody(QUERY_QUOTING_RESPONSE))

        // When
        val thrown =
            catchThrowable { storage.executeQuery(QueryRequest("SELECT foo FROM bar", emptyList()), "Report:1") }

        // Then: this failure is logged at ERROR, and its message becomes the title of a Sentry event,
        // so it must not contain the response body
        assertThat(thrown).isInstanceOf(ExternalSystemException::class.java)
        assertThat((thrown as ExternalSystemException).code)
            .isEqualTo(SqsQueryStorage.SqsQueryStorageErrorCode.QUERY_EXECUTION_FAILED)
        assertThat(thrown.message).contains("Report:1", "$status")
        assertThat(thrown.message).doesNotContain("SELECT foo FROM bar")
    }

    @Test
    fun `should log the SQS response body of a failed query at DEBUG only`() {
        // Given
        val logger = LoggerFactory.getLogger(SqsQueryStorage::class.java) as Logger
        val previousLevel = logger.level
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        logger.level = Level.DEBUG
        logger.addAppender(appender)
        mockWebServer.enqueue(MockResponse().setResponseCode(500).setBody(QUERY_QUOTING_RESPONSE))

        // When
        try {
            catchThrowable { storage.executeQuery(QueryRequest("SELECT foo FROM bar", emptyList()), "Report:1") }
        } finally {
            logger.detachAppender(appender)
            logger.level = previousLevel
        }

        // Then
        assertThat(appender.list).hasSize(1)
        assertThat(appender.list.first().level).isEqualTo(Level.DEBUG)
        assertThat(appender.list.first().formattedMessage).contains("Report:1", QUERY_QUOTING_RESPONSE)
    }
}
