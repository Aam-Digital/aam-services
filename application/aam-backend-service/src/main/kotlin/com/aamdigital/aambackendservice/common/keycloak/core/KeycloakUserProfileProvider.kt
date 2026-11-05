package com.aamdigital.aambackendservice.common.keycloak.core

import com.aamdigital.aambackendservice.common.keycloak.di.AamKeycloakConfig
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import jakarta.ws.rs.NotFoundException
import org.keycloak.admin.client.Keycloak
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.Optional

/**
 * Reads user profiles from Keycloak.
 *
 * The entity linked to an account is stored in the user attribute [LINKED_ENTITY_ATTRIBUTE] (which
 * the frontend receives as the `username` claim). A value without an entity type prefix is a legacy
 * `User` entity id, as in the frontend.
 *
 * Profiles (including "no such user") are cached for [cacheDuration], so that e.g. sending several
 * notification emails to one user does not ask Keycloak each time. Failed lookups are not cached.
 */
class KeycloakUserProfileProvider(
    private val keycloak: Keycloak,
    private val keycloakConfig: AamKeycloakConfig,
    cacheDuration: Duration = DEFAULT_CACHE_DURATION
) : UserProfileProvider {
    companion object {
        const val LINKED_ENTITY_ATTRIBUTE = "exact_username"
        private const val LEGACY_USER_ENTITY_TYPE = "User"
        private const val MAX_CACHED_PROFILES = 10_000L
        val DEFAULT_CACHE_DURATION: Duration = Duration.ofMinutes(5)
    }

    private val logger = LoggerFactory.getLogger(javaClass)

    private val cache: Cache<String, Optional<UserProfile>> =
        Caffeine
            .newBuilder()
            .expireAfterWrite(cacheDuration)
            .maximumSize(MAX_CACHED_PROFILES)
            .build()

    override fun getUserProfile(userIdentifier: String): UserProfile? =
        cache.get(userIdentifier) { Optional.ofNullable(lookupUserProfile(userIdentifier)) }.orElse(null)

    private fun lookupUserProfile(userIdentifier: String): UserProfile? {
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
                logger.debug("User not found in Keycloak: {}", userIdentifier)
                return null
            } catch (ex: Exception) {
                throw UserProfileLookupException("Could not look up user $userIdentifier in Keycloak", ex)
            }
        if (user == null) {
            return null
        }

        return UserProfile(
            userIdentifier = userIdentifier,
            email = user.email,
            linkedEntityId = linkedEntityId(user.attributes)
        )
    }

    private fun linkedEntityId(attributes: Map<String, List<String>>?): String? {
        val entityId =
            attributes
                ?.get(LINKED_ENTITY_ATTRIBUTE)
                ?.firstOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: return null

        return if (entityId.contains(":")) entityId else "$LEGACY_USER_ENTITY_TYPE:$entityId"
    }
}
