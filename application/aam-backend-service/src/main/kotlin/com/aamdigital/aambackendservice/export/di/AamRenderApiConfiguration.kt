package com.aamdigital.aambackendservice.export.di

import com.aamdigital.aambackendservice.common.auth.core.AuthConfig
import com.aamdigital.aambackendservice.common.auth.core.AuthProvider
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient


@ConfigurationProperties("aam-render-api-client-configuration")
@ConditionalOnProperty(
    prefix = "features.export-api",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = false
)
class AamRenderApiClientConfiguration(
    val basePath: String,
    val authConfig: AuthConfig? = null,
    val responseTimeoutInSeconds: Int = 30,
)

@Configuration
@ConditionalOnProperty(
    prefix = "features.export-api",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = false
)
class AamRenderApiConfiguration {

    @Bean(name = ["aam-render-api-client"])
    fun aamRenderApiClient(
        @Qualifier("aam-keycloak") authProvider: AuthProvider,
        configuration: AamRenderApiClientConfiguration
    ): RestClient {
        val clientBuilder = RestClient.builder()
            .baseUrl(configuration.basePath)

        if (configuration.authConfig != null) {
            clientBuilder.defaultRequest { request ->
                val token =
                    authProvider.fetchToken(configuration.authConfig)
                request.headers {
                    it.set(HttpHeaders.AUTHORIZATION, "Bearer ${token.token}")
                }
            }
        }

        clientBuilder.requestFactory(SimpleClientHttpRequestFactory().apply {
            setReadTimeout(configuration.responseTimeoutInSeconds * 1000)
            setConnectTimeout(configuration.responseTimeoutInSeconds * 1000)
        })

        return clientBuilder.build()
    }
}
