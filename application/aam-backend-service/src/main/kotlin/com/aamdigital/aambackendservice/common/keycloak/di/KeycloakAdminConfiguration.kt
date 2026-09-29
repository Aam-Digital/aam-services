package com.aamdigital.aambackendservice.common.keycloak.di

import org.keycloak.OAuth2Constants
import org.keycloak.admin.client.JacksonProvider
import org.keycloak.admin.client.Keycloak
import org.keycloak.admin.client.KeycloakBuilder
import org.keycloak.admin.client.spi.ResteasyClientClassicProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration
import java.util.concurrent.TimeUnit

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
    companion object {
        private val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(5)
        private val READ_TIMEOUT: Duration = Duration.ofSeconds(15)
    }

    @Bean
    fun keycloak(aamKeycloakConfig: AamKeycloakConfig): Keycloak =
        KeycloakBuilder
            .builder()
            .serverUrl(aamKeycloakConfig.serverUrl)
            .realm(aamKeycloakConfig.realm)
            .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
            .clientId(aamKeycloakConfig.clientId)
            .clientSecret(aamKeycloakConfig.clientSecret)
            .resteasyClient(
                // same client as the Keycloak default, but with timeouts: the startup client scope check
                // (KeycloakClientScopeConfiguration) must not block the application on an unresponsive Keycloak
                ResteasyClientClassicProvider
                    .createClientBuilder()
                    .register(JacksonProvider::class.java, JACKSON_PROVIDER_PRIORITY)
                    .connectTimeout(CONNECT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
                    .readTimeout(READ_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
                    .build()
            ).build()
}

/** Priority the Keycloak admin client registers its own [JacksonProvider] with. */
private const val JACKSON_PROVIDER_PRIORITY = 100
