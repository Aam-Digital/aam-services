package com.aamdigital.aambackendservice.auth.di

import com.aamdigital.aambackendservice.auth.core.AuthProvider
import com.aamdigital.aambackendservice.auth.core.KeycloakAuthProvider
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient


@ConfigurationProperties("aam-keycloak-client-configuration")
data class KeycloakConfiguration(
    val maxInMemorySizeInMegaBytes: Int = 16,
)

@Configuration
class AuthConfiguration {
    companion object {
        const val MEGA_BYTES_MULTIPLIER = 1024 * 1024
    }

    @Bean(name = ["aam-keycloak-client"])
    fun aamKeycloakWebClient(
        configuration: KeycloakConfiguration,
    ): WebClient {
        val clientBuilder =
            WebClient.builder()
                .codecs {
                    it.defaultCodecs()
                        .maxInMemorySize(configuration.maxInMemorySizeInMegaBytes * MEGA_BYTES_MULTIPLIER)
                }

        return clientBuilder.clientConnector(ReactorClientHttpConnector(HttpClient.create())).build()
    }

    @Bean(name = ["aam-keycloak"])
    fun aamKeycloakAuthProvider(
        @Qualifier("aam-keycloak-client") webClient: WebClient,
        objectMapper: ObjectMapper,
    ): AuthProvider =
        KeycloakAuthProvider(webClient = webClient, objectMapper = objectMapper)

}
