package com.aamdigital.aambackendservice.common.permission.di

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.concurrent.TimeUnit

class PermissionConfigurationTest {
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

    private fun client(responseTimeoutInSeconds: Int) =
        PermissionConfiguration().permissionCheckClient(
            ReplicationBackendClientConfiguration(
                basePath = mockWebServer.url("/").toString(),
                basicAuthUsername = "admin",
                basicAuthPassword = "secret",
                responseTimeoutInSeconds = responseTimeoutInSeconds
            )
        )

    @Test
    fun `should give up on an unresponsive replication-backend and deny instead of blocking`() {
        // Given
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"user-1":{"permitted":true}}""")
                .setHeadersDelay(10, TimeUnit.SECONDS)
        )
        val client = client(responseTimeoutInSeconds = 1)

        // When
        val startedAt = System.nanoTime()
        val result = client.checkPermissions(userIds = listOf("user-1"), entityId = "Child:1")
        val elapsed = Duration.ofNanos(System.nanoTime() - startedAt)

        // Then
        assertThat(result).isEmpty()
        assertThat(elapsed).isLessThan(Duration.ofSeconds(5))
    }

    @Test
    fun `should return the permissions of a replication-backend that answers in time`() {
        // Given
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"user-1":{"permitted":true},"user-2":{"permitted":false}}""")
        )
        val client = client(responseTimeoutInSeconds = 5)

        // When
        val result = client.checkPermissions(userIds = listOf("user-1", "user-2"), entityId = "Child:1")

        // Then
        assertThat(result).isEqualTo(mapOf("user-1" to true, "user-2" to false))
        val request = mockWebServer.takeRequest(5, TimeUnit.SECONDS)!!
        assertThat(request.path).isEqualTo("/permissions/check")
        assertThat(request.getHeader("Authorization")).startsWith("Basic ")
    }
}
