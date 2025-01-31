package com.aamdigital.aamintegration.authentication.di

import com.aamdigital.aamintegration.authentication.core.AamKeycloakAuthenticationProvider
import com.aamdigital.aamintegration.authentication.core.AuthenticationProvider
import org.keycloak.admin.client.Keycloak
import org.keycloak.admin.client.KeycloakBuilder
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@ConfigurationProperties("aam-keycloak-config")
class AamKeycloakConfig(
    val serverUrl: String,
    val realm: String,
    val clientId: String,
    val clientSecret: String,
)

@Configuration
class KeycloakConfiguration {

    @Bean
    fun keycloak(
        aamKeycloakConfig: AamKeycloakConfig,
    ): Keycloak = KeycloakBuilder.builder()
        .serverUrl(aamKeycloakConfig.serverUrl)
        .realm(aamKeycloakConfig.realm)
        .grantType(org.keycloak.OAuth2Constants.CLIENT_CREDENTIALS)
        .clientId(aamKeycloakConfig.clientId)
        .clientSecret(aamKeycloakConfig.clientSecret)
        .build()

    @Bean
    fun aamKeycloakAuthenticatorProvider(
        keycloak: Keycloak,
    ): AuthenticationProvider = AamKeycloakAuthenticationProvider(
        keycloak = keycloak
    )
}
