package com.aamdigital.aambackendservice.thirdpartyauthentication.di

import com.aamdigital.aambackendservice.common.domain.ApplicationConfig
import com.aamdigital.aambackendservice.common.keycloak.di.AamKeycloakConfig
import com.aamdigital.aambackendservice.thirdpartyauthentication.ConditionalOnThirdPartyAuthenticationEnabled
import com.aamdigital.aambackendservice.thirdpartyauthentication.core.AamKeycloakAuthenticationProvider
import com.aamdigital.aambackendservice.thirdpartyauthentication.core.AuthenticationProvider
import com.aamdigital.aambackendservice.thirdpartyauthentication.core.ThirdPartyAuthenticatorActivation
import org.keycloak.admin.client.Keycloak
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnThirdPartyAuthenticationEnabled
class ThirdPartyAuthenticationKeycloakConfiguration {
    companion object {
        /** Path under which the app reaches this backend's API (the frontend's API_PROXY_PREFIX). */
        private const val API_PATH = "/api"
    }

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Switches on the third-party authenticator in the realm's login flow on startup, see
     * [ThirdPartyAuthenticatorActivation].
     */
    @Bean
    fun thirdPartyAuthenticatorActivationStartup(
        keycloak: Keycloak,
        aamKeycloakConfig: AamKeycloakConfig,
        applicationConfig: ApplicationConfig
    ): ApplicationRunner =
        ApplicationRunner {
            if (aamKeycloakConfig.serverUrl.isBlank() || applicationConfig.baseUrl.isBlank()) {
                logger.warn(
                    "keycloak.server-url or application.base-url is not set, so the third-party authenticator " +
                        "in the Keycloak login flow cannot be switched on. Users of external systems cannot be " +
                        "signed in until it is enabled and configured in the Keycloak admin console."
                )
                return@ApplicationRunner
            }

            ThirdPartyAuthenticatorActivation(
                keycloak = keycloak,
                realm = aamKeycloakConfig.realm,
                apiBaseUrl = applicationConfig.normalizedBaseUrl.trimEnd('/') + API_PATH
            ).activate()
        }

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
