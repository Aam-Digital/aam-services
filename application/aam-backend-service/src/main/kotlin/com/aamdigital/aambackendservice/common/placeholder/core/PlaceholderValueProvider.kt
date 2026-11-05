package com.aamdigital.aambackendservice.common.placeholder.core

import com.aamdigital.aambackendservice.common.keycloak.core.UserProfileLookupException
import com.aamdigital.aambackendservice.common.keycloak.core.UserProfileProvider
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Thrown when placeholder values cannot be looked up for now (e.g. Keycloak is unreachable), as
 * opposed to a placeholder that has no value for a user, which a lookup reports by leaving it out.
 */
class PlaceholderValueLookupException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)

/**
 * Looks up the values of all [Placeholders] for one user account, to be used with [PlaceholderResolver].
 *
 * - [Placeholders.CURRENT_USER] is the entity linked to the user account, from [userProfileProvider].
 *
 * Without a [userProfileProvider] (Keycloak admin access is not configured) user placeholders have
 * no value, so configuration that uses one is ignored by its consumer. That is logged once, on the
 * first lookup that actually needs one, so an instance that uses none is not told about it.
 */
class PlaceholderValueProvider(
    private val userProfileProvider: UserProfileProvider?
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val warnedUnavailable = AtomicBoolean(false)

    /**
     * @param userIdentifier the id of the user account (the Keycloak user id)
     * @param placeholders the placeholders that are needed, so nothing unused is looked up
     * @return the values of those [placeholders] that the user has; a placeholder without a value
     *     (e.g. no entity linked to the account) is left out
     * @throws PlaceholderValueLookupException if the values cannot be looked up for now
     */
    fun getValues(
        userIdentifier: String,
        placeholders: Set<String>
    ): Map<String, Any> {
        if (Placeholders.CURRENT_USER !in placeholders) {
            return emptyMap()
        }

        val linkedEntityId = lookupLinkedEntityId(userIdentifier, placeholders) ?: return emptyMap()
        return mapOf(Placeholders.CURRENT_USER to linkedEntityId)
    }

    private fun lookupLinkedEntityId(
        userIdentifier: String,
        placeholders: Set<String>
    ): String? {
        if (userProfileProvider == null) {
            if (warnedUnavailable.compareAndSet(false, true)) {
                logger.warn(
                    "Placeholders {} are used, but cannot be resolved because Keycloak admin access " +
                        "(keycloak.server-url) is not configured. Configuration using them is ignored.",
                    placeholders
                )
            }
            return null
        }

        return try {
            userProfileProvider.getUserProfile(userIdentifier)?.linkedEntityId
        } catch (ex: UserProfileLookupException) {
            throw PlaceholderValueLookupException("Could not look up placeholder values of $userIdentifier", ex)
        }
    }
}
