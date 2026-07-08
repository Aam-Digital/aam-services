package com.aamdigital.aambackendservice.reporting.report.di

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

class SqsConfigurationTest {
    private lateinit var mockWebServer: MockWebServer

    @BeforeEach
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
    }

    @AfterEach
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `should not reuse connections so that SQS cannot reset a pooled connection mid-query`() {
        // Given
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("[]"))
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("[]"))
        val client =
            SqsConfiguration().sqsRestClient(
                SqsClientConfiguration(
                    basePath = mockWebServer.url("/").toString(),
                    basicAuthUsername = "admin",
                    basicAuthPassword = "secret",
                )
            )

        // When
        repeat(2) {
            client.post().uri("/app/_design/sqlite:config").body("{}").retrieve().body(String::class.java)
        }

        // Then
        val firstRequest = mockWebServer.takeRequest(5, TimeUnit.SECONDS)!!
        val secondRequest = mockWebServer.takeRequest(5, TimeUnit.SECONDS)!!
        assertThat(firstRequest.getHeader("Connection")).isEqualToIgnoringCase("close")
        assertThat(secondRequest.getHeader("Connection")).isEqualToIgnoringCase("close")
        assertThat(firstRequest.getHeader("Authorization")).startsWith("Basic ")
        // sequenceNumber restarts at 0 for each TCP connection: both being 0 proves no keep-alive reuse
        assertThat(firstRequest.sequenceNumber).isEqualTo(0)
        assertThat(secondRequest.sequenceNumber).isEqualTo(0)
    }
}
