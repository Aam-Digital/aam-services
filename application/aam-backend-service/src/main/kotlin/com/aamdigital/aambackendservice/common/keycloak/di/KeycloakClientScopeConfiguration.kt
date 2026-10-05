package com.aamdigital.aambackendservice.common.keycloak.di

import com.aamdigital.aambackendservice.common.keycloak.core.ClientScopeInitializer
import com.aamdigital.aambackendservice.common.keycloak.core.ClientScopeRequest
import com.aamdigital.aambackendservice.common.keycloak.core.KeycloakClientScopeAdministration
import org.keycloak.admin.client.Keycloak
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Runs the [ClientScopeInitializer] on startup for the [ClientScopeRequest]s of all enabled feature modules.
 */
@Configuration
class KeycloakClientScopeConfiguration {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Bean
    fun keycloakClientScopeStartup(
        clientScopeRequests: ObjectProvider<ClientScopeRequest>,
        keycloakProvider: ObjectProvider<Keycloak>,
        keycloakConfigProvider: ObjectProvider<AamKeycloakConfig>
    ): ApplicationRunner =
        ApplicationRunner {
            val requests = clientScopeRequests.orderedStream().toList()
            if (requests.isEmpty()) {
                return@ApplicationRunner
            }

            val keycloak = keycloakProvider.ifAvailable
            val keycloakConfig = keycloakConfigProvider.ifAvailable
            // an empty KEYCLOAK_SERVERURL (as in the env template) still creates the Keycloak bean
            if (keycloak == null || keycloakConfig == null || keycloakConfig.serverUrl.isBlank()) {
                logger.warn(
                    "Keycloak admin access is not configured (keycloak.server-url unset or empty), " +
                        "so the client scopes {} cannot be checked. Make sure they exist in the realm " +
                        "and are assigned as Default scopes to the API clients that need them, " +
                        "otherwise those API clients are denied access.",
                    requests.map { it.name }
                )
                return@ApplicationRunner
            }

            ClientScopeInitializer(
                administration =
                    KeycloakClientScopeAdministration(
                        keycloak = keycloak,
                        realm = keycloakConfig.realm,
                        backendClientId = keycloakConfig.clientId
                    ),
                clientScopeRequests = requests
            ).initialize()
        }
}
