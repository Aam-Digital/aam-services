package com.aamdigital.aambackendservice.common.couchdb.core

import com.aamdigital.aambackendservice.common.error.ExternalSystemException
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient

/**
 * Focused tests for the fault-tolerant response handling (issue #25): remote responses are read as a
 * raw String first so the actual payload survives a parsing failure or a non-2xx status.
 */
class DefaultCouchDbClientTest {
    private lateinit var mockWebServer: MockWebServer
    private lateinit var client: DefaultCouchDbClient

    @BeforeEach
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        val restClient =
            RestClient
                .builder()
                .baseUrl(mockWebServer.url("/").toString())
                .build()
        client = DefaultCouchDbClient(restClient, jacksonObjectMapper())
    }

    @AfterEach
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `getDatabaseChanges surfaces the raw response when the body cannot be parsed`() {
        mockWebServer.enqueue(MockResponse().setBody("this is not the expected json"))

        assertThatThrownBy {
            client.getDatabaseChanges("app", LinkedMultiValueMap())
        }.isInstanceOf(ExternalSystemException::class.java)
            .hasMessageContaining("this is not the expected json")
    }

    @Test
    fun `getDatabaseChanges includes the response body on a non-2xx status`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(500).setBody("couchdb internal error detail"))

        assertThatThrownBy {
            client.getDatabaseChanges("app", LinkedMultiValueMap())
        }.isInstanceOf(ExternalSystemException::class.java)
            .hasMessageContaining("couchdb internal error detail")
    }

    @Test
    fun `allDatabases parses a successful list response`() {
        mockWebServer.enqueue(MockResponse().setBody("""["_users","app"]"""))

        val result = client.allDatabases()

        assertThat(result).containsExactly("_users", "app")
    }
}
