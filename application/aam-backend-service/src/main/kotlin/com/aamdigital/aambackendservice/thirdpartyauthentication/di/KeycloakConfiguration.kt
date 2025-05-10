package com.aamdigital.aambackendservice.thirdpartyauthentication.di

import com.aamdigital.aambackendservice.thirdpartyauthentication.core.AamKeycloakAuthenticationProvider
import com.aamdigital.aambackendservice.thirdpartyauthentication.core.AuthenticationProvider
import org.keycloak.admin.client.Keycloak
import org.keycloak.admin.client.KeycloakBuilder
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@ConditionalOnProperty(
    prefix = "features.third-party-authentication",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = false
)
@ConfigurationProperties("keycloak")
class AamKeycloakConfig(
    val serverUrl: String,
    val realm: String,
    val clientId: String,
    val clientSecret: String,
    val applicationUrl: String,
)

@ConditionalOnProperty(
    prefix = "features.third-party-authentication",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = false
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
