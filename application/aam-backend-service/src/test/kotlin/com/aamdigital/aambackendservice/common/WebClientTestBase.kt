package com.aamdigital.aambackendservice.common

import com.aamdigital.aambackendservice.common.auth.core.AuthConfig
import com.aamdigital.aambackendservice.common.auth.core.AuthProvider
import com.aamdigital.aambackendservice.common.auth.core.TokenResponse
import com.aamdigital.aambackendservice.export.di.AamRenderApiClientConfiguration
import com.aamdigital.aambackendservice.export.di.AamRenderApiConfiguration
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.springframework.web.client.RestClient

open class WebClientTestBase {
    companion object {
        const val WEBSERVER_PORT = 6000
        val objectMapper = jacksonObjectMapper()
        lateinit var restClient: RestClient
        lateinit var mockWebServer: MockWebServer

        @JvmStatic
        @BeforeAll
        fun init() {
            val config = AamRenderApiConfiguration()
            restClient = config.aamRenderApiClient(
                authProvider = object : AuthProvider {
                    override fun fetchToken(authClientConfig: AuthConfig): TokenResponse = TokenResponse("dummy-token")
                },
                configuration = AamRenderApiClientConfiguration(
                    basePath = "http://localhost:$WEBSERVER_PORT",
                    responseTimeoutInSeconds = 10
                )
            )
        }
    }

    @BeforeEach
    fun beforeEach() {
        startWebserver()
        setUp()
    }

    @AfterEach
    fun afterEach() {
        stopWebserver()
        cleanUp()
    }

    open fun setUp() {}
    open fun cleanUp() {}

    private fun startWebserver() {
        mockWebServer = MockWebServer()
        mockWebServer.start(WEBSERVER_PORT)
    }

    private fun stopWebserver() {
        mockWebServer.shutdown()
    }
}
