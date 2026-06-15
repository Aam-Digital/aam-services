package com.aamdigital.aambackendservice.reporting.report.sqs

import com.aamdigital.aambackendservice.common.error.ExternalSystemException
import com.aamdigital.aambackendservice.common.error.InvalidArgumentException
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.web.client.RestClient

class SqsQueryStorageTest {
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

        val result = storage.executeQuery(QueryRequest("SELECT foo FROM bar", emptyList()))

        assertThat(result.readBytes().decodeToString()).isEqualTo("""[{"foo":1}]""")
    }

    @Test
    fun `should throw InvalidArgumentException carrying the SQS body on 4xx (invalid query)`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(400).setBody("no such column: foo"))

        val thrown = catchThrowable { storage.executeQuery(QueryRequest("SELECT foo FROM bar", emptyList())) }

        assertThat(thrown).isInstanceOf(InvalidArgumentException::class.java)
        assertThat((thrown as InvalidArgumentException).code)
            .isEqualTo(SqsQueryStorage.SqsQueryStorageErrorCode.QUERY_FAILED)
        assertThat(thrown.message).contains("no such column: foo")
    }

    @Test
    fun `should throw ExternalSystemException on 5xx`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(500).setBody("internal error"))

        val thrown = catchThrowable { storage.executeQuery(QueryRequest("SELECT foo FROM bar", emptyList())) }

        assertThat(thrown).isInstanceOf(ExternalSystemException::class.java)
        assertThat((thrown as ExternalSystemException).code)
            .isEqualTo(SqsQueryStorage.SqsQueryStorageErrorCode.QUERY_EXECUTION_FAILED)
    }
}
