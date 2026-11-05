package com.aamdigital.aambackendservice.admin.apiclient.core

import com.aamdigital.aambackendservice.admin.apiclient.core.ApiClientAdministration.Companion.CLIENT_ID_PREFIX
import com.aamdigital.aambackendservice.common.error.InvalidArgumentException
import java.text.Normalizer
import java.util.Random

/** Longest name of an API client. */
const val MAX_API_CLIENT_NAME_LENGTH = 100

/** A client id chosen by the user: the prefix, then letters, digits, ".", "_" or "-" (64 characters at most). */
private val CLIENT_ID_PATTERN = Regex("^${Regex.escape(CLIENT_ID_PREFIX)}[A-Za-z0-9][A-Za-z0-9._-]{0,59}$")

private const val MAX_SLUG_LENGTH = 30
private const val SUFFIX_LENGTH = 6
private const val SUFFIX_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789"
private val DIACRITICS = Regex("\\p{M}+")
private val NON_ALPHANUMERIC = Regex("[^a-z0-9]+")

/**
 * An API client to be created.
 *
 * @property clientId starts with [CLIENT_ID_PREFIX], see [clientIdFor]
 * @property scopes client scopes to assign as Default scopes; they have to exist in the realm
 */
internal data class NewApiClient(
    val clientId: String,
    val name: String,
    val scopes: Set<String>,
    val createdBy: String?
)

/**
 * Checks the request to create an API client.
 *
 * @param clientId the client id chosen by the user, or null to generate one ([clientIdFor])
 * @param offeredScopes the client scopes that API clients can be given
 * @throws InvalidArgumentException with an [ApiClientAdministrationError] code if the request is invalid
 */
internal fun validateNewApiClient(
    name: String,
    clientId: String?,
    scopes: Set<String>,
    offeredScopes: Set<String>
) {
    val (code, message) =
        when {
            name.isBlank() || name.length > MAX_API_CLIENT_NAME_LENGTH -> {
                ApiClientAdministrationError.INVALID_NAME to
                    "The name of an API client must have 1 to $MAX_API_CLIENT_NAME_LENGTH characters"
            }

            clientId != null && !CLIENT_ID_PATTERN.matches(clientId) -> {
                ApiClientAdministrationError.INVALID_CLIENT_ID to
                    "The client id of an API client must start with '$CLIENT_ID_PREFIX', followed by letters, " +
                    "digits, '.', '_' or '-' (64 characters at most)"
            }

            else -> {
                return validateScopes(scopes, offeredScopes)
            }
        }
    throw InvalidArgumentException(message = message, code = code)
}

/**
 * Checks the client scopes to give an API client (when creating it or changing its access).
 *
 * @param offeredScopes the client scopes that API clients can be given
 * @throws InvalidArgumentException with an [ApiClientAdministrationError] code if the scopes are invalid
 */
internal fun validateScopes(
    scopes: Set<String>,
    offeredScopes: Set<String>
) {
    val unknownScopes = scopes - offeredScopes
    val (code, message) =
        when {
            scopes.isEmpty() -> {
                ApiClientAdministrationError.NO_SCOPE to "An API client needs at least one client scope"
            }

            unknownScopes.isNotEmpty() -> {
                ApiClientAdministrationError.UNKNOWN_SCOPE to
                    "These client scopes cannot be given to an API client: $unknownScopes"
            }

            else -> {
                return
            }
        }
    throw InvalidArgumentException(message = message, code = code)
}

/**
 * The client id of an API client: [CLIENT_ID_PREFIX], the name in lowercase letters and digits, and a random suffix,
 * so that it is unique and cannot be guessed from the name.
 */
internal fun clientIdFor(
    name: String,
    random: Random
): String {
    val slug =
        Normalizer
            .normalize(name, Normalizer.Form.NFD)
            .replace(DIACRITICS, "")
            .lowercase()
            .replace(NON_ALPHANUMERIC, "-")
            .trim('-')
            .take(MAX_SLUG_LENGTH)
            .trimEnd('-')
    val suffix = (1..SUFFIX_LENGTH).map { SUFFIX_ALPHABET[random.nextInt(SUFFIX_ALPHABET.length)] }.joinToString("")

    return if (slug.isEmpty()) "$CLIENT_ID_PREFIX$suffix" else "$CLIENT_ID_PREFIX$slug-$suffix"
}
