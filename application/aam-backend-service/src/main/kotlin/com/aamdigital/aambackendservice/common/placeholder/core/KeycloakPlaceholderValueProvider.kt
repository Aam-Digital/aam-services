package com.aamdigital.aambackendservice.common.placeholder.core

import com.aamdigital.aambackendservice.common.keycloak.di.AamKeycloakConfig
import jakarta.ws.rs.NotFoundException
import org.keycloak.admin.client.Keycloak
import org.slf4j.LoggerFactory

/**
 * Looks up placeholder values from the user account in Keycloak.
 *
 * [Placeholders.CURRENT_USER] is the entity linked to the account, stored in the user attribute
 * [USER_ENTITY_ATTRIBUTE] (which the frontend receives as the `username` claim). A value without an
 * entity type prefix is a legacy `User` entity id, as in the frontend.
 */
class KeycloakPlaceholderValueProvider(
    private val keycloak: Keycloak,
    private val keycloakConfig: AamKeycloakConfig
) : PlaceholderValueProvider {
    companion object {
        const val USER_ENTITY_ATTRIBUTE = "exact_username"
        private const val LEGACY_USER_ENTITY_TYPE = "User"
    }

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun getValues(
        userIdentifier: String,
        placeholders: Set<String>
    ): Map<String, Any> {
        if (Placeholders.CURRENT_USER !in placeholders) {
            return emptyMap()
        }

        val userEntityId = lookupUserEntityId(userIdentifier) ?: return emptyMap()
        return mapOf(Placeholders.CURRENT_USER to userEntityId)
    }

    private fun lookupUserEntityId(userIdentifier: String): String? {
        val user =
            try {
                keycloak
                    .realm(keycloakConfig.realm)
                    .users()
                    .get(userIdentifier)
                    .toRepresentation()
            } catch (
                @Suppress("SwallowedException") ex: NotFoundException
            ) {
                logger.debug("User not found in Keycloak, so it has no placeholder values: {}", userIdentifier)
                return null
            } catch (ex: Exception) {
                throw PlaceholderValueLookupException("Could not look up user $userIdentifier in Keycloak", ex)
            }

        val entityId =
            user
                ?.attributes
                ?.get(USER_ENTITY_ATTRIBUTE)
                ?.firstOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: return null

        return if (entityId.contains(":")) entityId else "$LEGACY_USER_ENTITY_TYPE:$entityId"
    }
}
