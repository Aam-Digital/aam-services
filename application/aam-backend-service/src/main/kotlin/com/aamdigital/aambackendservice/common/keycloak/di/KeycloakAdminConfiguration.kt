package com.aamdigital.aambackendservice.common.keycloak.di

import org.keycloak.OAuth2Constants
import org.keycloak.admin.client.Keycloak
import org.keycloak.admin.client.KeycloakBuilder
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@ConfigurationProperties("keycloak")
@ConditionalOnProperty(prefix = "keycloak", name = ["server-url"])
class AamKeycloakConfig(
    val serverUrl: String,
    val realm: String,
    val clientId: String,
    val clientSecret: String
)

@Configuration
@ConditionalOnProperty(prefix = "keycloak", name = ["server-url"])
class KeycloakAdminConfiguration {
    @Bean
    fun keycloak(aamKeycloakConfig: AamKeycloakConfig): Keycloak =
        KeycloakBuilder
            .builder()
            .serverUrl(aamKeycloakConfig.serverUrl)
            .realm(aamKeycloakConfig.realm)
            .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
            .clientId(aamKeycloakConfig.clientId)
            .clientSecret(aamKeycloakConfig.clientSecret)
            .build()
}
