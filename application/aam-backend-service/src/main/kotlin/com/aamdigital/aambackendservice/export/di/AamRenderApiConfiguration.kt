package com.aamdigital.aambackendservice.export.di

import com.aamdigital.aambackendservice.auth.core.AuthConfig
import com.aamdigital.aambackendservice.auth.core.AuthProvider
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient


@ConfigurationProperties("aam-render-api-client-configuration")
class AamRenderApiClientConfiguration(
    val basePath: String,
    val authConfig: AuthConfig,
    val maxInMemorySizeInMegaBytes: Int = 16,
)

@Configuration
class AamRenderApiConfiguration {
    companion object {
        const val MEGA_BYTES_MULTIPLIER = 1024 * 1024
    }

    @Bean(name = ["aam-render-api-client"])
    fun aamRenderApiClient(
        @Qualifier("aam-keycloak") authProvider: AuthProvider,
        configuration: AamRenderApiClientConfiguration
    ): WebClient {
        val clientBuilder =
            WebClient.builder()
                .codecs {
                    it.defaultCodecs()
                        .maxInMemorySize(configuration.maxInMemorySizeInMegaBytes * MEGA_BYTES_MULTIPLIER)
                }
                .baseUrl(configuration.basePath)
                .filter { request, next ->
                    authProvider.fetchToken(configuration.authConfig).map {
                        ClientRequest.from(request).header(HttpHeaders.AUTHORIZATION, "Bearer ${it.token}").build()
                    }.flatMap {
                        next.exchange(it)
                    }
                }

        return clientBuilder.clientConnector(ReactorClientHttpConnector(HttpClient.create())).build()
    }
}
