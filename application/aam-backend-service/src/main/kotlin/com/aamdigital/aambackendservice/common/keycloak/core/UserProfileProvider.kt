package com.aamdigital.aambackendservice.common.keycloak.core

/**
 * The profile of a user account.
 *
 * @param userIdentifier the id of the account (the Keycloak user id)
 * @param linkedEntityId the id of the entity linked to the account (e.g. `User:alice`), if any
 */
data class UserProfile(
    val userIdentifier: String,
    val email: String?,
    val linkedEntityId: String?
)

/**
 * Thrown when a user profile cannot be looked up for now (e.g. Keycloak is unreachable), as
 * opposed to an account that does not exist, which a lookup reports as `null`.
 */
class UserProfileLookupException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)

/** Looks up the profile of a user account. */
interface UserProfileProvider {
    /**
     * @return the profile, or `null` if there is no such account
     * @throws UserProfileLookupException if the profile cannot be looked up for now
     */
    fun getUserProfile(userIdentifier: String): UserProfile?
}
