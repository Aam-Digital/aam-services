package com.aamdigital.aambackendservice.common.placeholder.core

/**
 * Thrown when placeholder values cannot be looked up for now (e.g. Keycloak is unreachable), as
 * opposed to a placeholder that has no value for a user, which a lookup reports by leaving it out.
 */
class PlaceholderValueLookupException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)

/** Looks up the values of [Placeholders] for one user account, to be used with [PlaceholderResolver]. */
interface PlaceholderValueProvider {
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
    ): Map<String, Any>
}
