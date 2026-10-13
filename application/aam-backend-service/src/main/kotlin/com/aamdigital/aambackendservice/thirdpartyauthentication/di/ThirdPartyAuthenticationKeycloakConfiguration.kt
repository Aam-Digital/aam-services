package com.aamdigital.aambackendservice.thirdpartyauthentication.di

import com.aamdigital.aambackendservice.common.domain.ApplicationConfig
import com.aamdigital.aambackendservice.common.keycloak.di.AamKeycloakConfig
import com.aamdigital.aambackendservice.common.scheduling.ScheduledJobBackoff
import com.aamdigital.aambackendservice.thirdpartyauthentication.ConditionalOnThirdPartyAuthenticationEnabled
import com.aamdigital.aambackendservice.thirdpartyauthentication.core.AamKeycloakAuthenticationProvider
import com.aamdigital.aambackendservice.thirdpartyauthentication.core.AuthenticationProvider
import com.aamdigital.aambackendservice.thirdpartyauthentication.core.ThirdPartyAuthenticatorActivation
import org.keycloak.admin.client.Keycloak
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.TaskScheduler
import java.time.Instant

@Configuration
@ConditionalOnThirdPartyAuthenticationEnabled
class ThirdPartyAuthenticationKeycloakConfiguration {
    companion object {
        /** Path under which the app reaches this backend's API (the frontend's API_PROXY_PREFIX). */
        private const val API_PATH = "/api"

        /** Tries again while Keycloak cannot be reached, e.g. because it starts after the backend. */
        internal const val MAX_RETRIES = 8
        private const val MAX_RETRY_DELAY_MS = 300_000L
    }

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Switches on the third-party authenticator in the realm's login flow on startup, see
     * [ThirdPartyAuthenticatorActivation]. While Keycloak cannot be reached, it tries again in the background
     * (with a growing delay, about 15 minutes in total).
     */
    @Bean
    fun thirdPartyAuthenticatorActivationStartup(
        keycloak: Keycloak,
        aamKeycloakConfig: AamKeycloakConfig,
        applicationConfig: ApplicationConfig,
        taskScheduler: TaskScheduler
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

            val activation =
                ThirdPartyAuthenticatorActivation(
                    keycloak = keycloak,
                    realm = aamKeycloakConfig.realm,
                    apiBaseUrl = applicationConfig.normalizedBaseUrl.trimEnd('/') + API_PATH
                )
            activateWithRetries(activation, taskScheduler, retry = 0)
        }

    private fun activateWithRetries(
        activation: ThirdPartyAuthenticatorActivation,
        taskScheduler: TaskScheduler,
        retry: Int
    ) {
        if (activation.activate()) {
            return
        }
        if (retry >= MAX_RETRIES) {
            logger.error(
                "Gave up setting up the third-party authenticator in the Keycloak login flow after {} retries. " +
                    "Users of external systems cannot be signed in until the backend is restarted with Keycloak " +
                    "reachable, or the step is added in the Keycloak admin console.",
                MAX_RETRIES
            )
            return
        }
        val delayMs = ScheduledJobBackoff.calculateBackoffMs(retry + 1, MAX_RETRY_DELAY_MS)
        logger.info("Trying again to set up the third-party authenticator in {} ms", delayMs)
        taskScheduler.schedule(
            { activateWithRetries(activation, taskScheduler, retry + 1) },
            Instant.now().plusMillis(delayMs)
        )
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
