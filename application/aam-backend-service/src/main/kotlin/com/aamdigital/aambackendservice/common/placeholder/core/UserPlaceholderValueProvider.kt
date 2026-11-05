package com.aamdigital.aambackendservice.common.placeholder.core

import com.aamdigital.aambackendservice.common.keycloak.core.UserProfileLookupException
import com.aamdigital.aambackendservice.common.keycloak.core.UserProfileProvider
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Derives the values of [Placeholders] from the user's profile.
 *
 * [Placeholders.CURRENT_USER] is the entity linked to the user account.
 *
 * Without a [userProfileProvider] (Keycloak admin access is not configured) no placeholder has a
 * value, so configuration that uses one is ignored by its consumer. That is logged once, on the first
 * lookup that actually needs a placeholder, so an instance that uses none is not told about it.
 */
class UserPlaceholderValueProvider(
    private val userProfileProvider: UserProfileProvider?
) : PlaceholderValueProvider {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val warnedUnavailable = AtomicBoolean(false)

    override fun getValues(
        userIdentifier: String,
        placeholders: Set<String>
    ): Map<String, Any> {
        if (Placeholders.CURRENT_USER !in placeholders) {
            return emptyMap()
        }

        if (userProfileProvider == null) {
            if (warnedUnavailable.compareAndSet(false, true)) {
                logger.warn(
                    "Placeholders {} are used, but cannot be resolved because Keycloak admin access " +
                        "(keycloak.server-url) is not configured. Configuration using them is ignored.",
                    placeholders
                )
            }
            return emptyMap()
        }

        val profile =
            try {
                userProfileProvider.getUserProfile(userIdentifier)
            } catch (ex: UserProfileLookupException) {
                throw PlaceholderValueLookupException("Could not look up placeholder values of $userIdentifier", ex)
            }

        val linkedEntityId = profile?.linkedEntityId ?: return emptyMap()
        return mapOf(Placeholders.CURRENT_USER to linkedEntityId)
    }
}
