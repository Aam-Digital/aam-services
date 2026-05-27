package com.aamdigital.aambackendservice.thirdpartyauthentication.di

import com.aamdigital.aambackendservice.thirdpartyauthentication.ConditionalOnThirdPartyAuthenticationEnabled
import com.aamdigital.aambackendservice.thirdpartyauthentication.core.AamKeycloakAuthenticationProvider
import com.aamdigital.aambackendservice.thirdpartyauthentication.core.AuthenticationProvider
import org.keycloak.admin.client.Keycloak
import org.keycloak.admin.client.KeycloakBuilder
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@ConditionalOnThirdPartyAuthenticationEnabled
@ConfigurationProperties("keycloak")
class AamKeycloakConfig(
    val serverUrl: String,
    val realm: String,
    val clientId: String,
    val clientSecret: String
)

@ConditionalOnThirdPartyAuthenticationEnabled
@Configuration
class KeycloakConfiguration {
    @Bean
    fun keycloak(aamKeycloakConfig: AamKeycloakConfig): Keycloak =
        KeycloakBuilder
            .builder()
            .serverUrl(aamKeycloakConfig.serverUrl)
            .realm(aamKeycloakConfig.realm)
            .grantType(org.keycloak.OAuth2Constants.CLIENT_CREDENTIALS)
            .clientId(aamKeycloakConfig.clientId)
            .clientSecret(aamKeycloakConfig.clientSecret)
            .build()

    @Bean
    fun aamKeycloakAuthenticatorProvider(
        keycloak: Keycloak,
        aamKeycloakConfig: AamKeycloakConfig
    ): AuthenticationProvider =
        AamKeycloakAuthenticationProvider(
            keycloak = keycloak,
            keycloakConfig = aamKeycloakConfig
        )
}
