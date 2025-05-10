package com.aamdigital.aambackendservice.common.auth.di

import com.aamdigital.aambackendservice.common.auth.core.AuthProvider
import com.aamdigital.aambackendservice.common.auth.core.KeycloakAuthProvider
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient

@Configuration
class AuthConfiguration {
    
    @Bean(name = ["aam-keycloak-client"])
    fun aamKeycloakRestClient(
    ): RestClient {
        val clientBuilder = RestClient.builder()
        return clientBuilder.build()
    }

    @Bean(name = ["aam-keycloak"])
    fun aamKeycloakAuthProvider(
        @Qualifier("aam-keycloak-client") webClient: RestClient,
        objectMapper: ObjectMapper,
    ): AuthProvider =
        KeycloakAuthProvider(httpClient = webClient, objectMapper = objectMapper)
}
