package com.aamdigital.aambackendservice.notification.core.create.email

import com.aamdigital.aambackendservice.common.keycloak.di.AamKeycloakConfig
import org.keycloak.admin.client.Keycloak
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.Cacheable

open class KeycloakUserEmailProvider(
    private val keycloak: Keycloak,
    private val keycloakConfig: AamKeycloakConfig
) : UserEmailProvider {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Cacheable(value = ["user-emails"], cacheManager = "notificationEmailCacheManager")
    open override fun lookupEmail(userIdentifier: String): String? =
        try {
            keycloak
                .realm(keycloakConfig.realm)
                .users()
                .get(userIdentifier)
                .toRepresentation()
                ?.email
        } catch (ex: Exception) {
            logger.warn("Failed to lookup email for user {}.", userIdentifier, ex)
            null
        }
}
