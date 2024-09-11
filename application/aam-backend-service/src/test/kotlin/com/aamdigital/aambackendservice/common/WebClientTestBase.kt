package com.aamdigital.aambackendservice.common

import com.aamdigital.aambackendservice.auth.core.AuthConfig
import com.aamdigital.aambackendservice.auth.core.AuthProvider
import com.aamdigital.aambackendservice.auth.core.TokenResponse
import com.aamdigital.aambackendservice.export.di.AamRenderApiClientConfiguration
import com.aamdigital.aambackendservice.export.di.AamRenderApiConfiguration
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

open class WebClientTestBase {
    companion object {
        const val WEBSERVER_PORT = 6000
        val objectMapper = jacksonObjectMapper()
        lateinit var webClient: WebClient
        lateinit var mockWebServer: MockWebServer

        @JvmStatic
        @BeforeAll
        fun init() {
            val config = AamRenderApiConfiguration()
            webClient = config.aamRenderApiClient(
                authProvider = object : AuthProvider {
                    override fun fetchToken(authClientConfig: AuthConfig): Mono<TokenResponse> = Mono.empty()
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
