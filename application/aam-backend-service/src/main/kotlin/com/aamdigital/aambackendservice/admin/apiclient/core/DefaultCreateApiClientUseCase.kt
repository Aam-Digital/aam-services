package com.aamdigital.aambackendservice.admin.apiclient.core

import com.aamdigital.aambackendservice.admin.apiclient.core.ApiClientAdministration.Companion.CLIENT_ID_PREFIX
import com.aamdigital.aambackendservice.common.domain.UseCaseOutcome
import com.aamdigital.aambackendservice.common.keycloak.core.ClientScopeRequest
import java.security.SecureRandom
import java.text.Normalizer

/**
 * Offers the client scopes of the enabled feature modules ([ClientScopeRequest]s).
 *
 * The client id is derived from the name, with a random suffix so that it is unique and cannot be guessed
 * from the name.
 */
class DefaultCreateApiClientUseCase(
    private val administration: ApiClientAdministration,
    clientScopeRequests: List<ClientScopeRequest>,
    private val random: SecureRandom = SecureRandom()
) : CreateApiClientUseCase() {
    companion object {
        const val MAX_NAME_LENGTH = 100
        private const val MAX_SLUG_LENGTH = 30
        private const val SUFFIX_LENGTH = 6
        private const val SUFFIX_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789"
        private val DIACRITICS = Regex("\\p{M}+")
        private val NON_ALPHANUMERIC = Regex("[^a-z0-9]+")
    }

    private val offeredScopes = clientScopeRequests.map { it.name }.toSet()

    override fun apply(request: CreateApiClientRequest): UseCaseOutcome<CreateApiClientData> {
        val name = request.name.trim()
        val invalidRequest = validate(name, request)
        if (invalidRequest != null) {
            return invalidRequest
        }

        val credentials =
            administration.createApiClient(
                NewApiClient(
                    clientId = clientIdFor(name),
                    name = name,
                    scopes = request.scopes,
                    createdBy = request.createdBy
                )
            )
        return UseCaseOutcome.Success(CreateApiClientData(credentials = credentials, scopes = request.scopes))
    }

    private fun validate(
        name: String,
        request: CreateApiClientRequest
    ): UseCaseOutcome.Failure<CreateApiClientData>? {
        val unknownScopes = request.scopes.filterNot { it in offeredScopes }

        return when {
            name.isEmpty() || name.length > MAX_NAME_LENGTH -> {
                UseCaseOutcome.Failure(
                    errorCode = CreateApiClientError.INVALID_NAME,
                    errorMessage = "The name of an API client must have 1 to $MAX_NAME_LENGTH characters"
                )
            }

            request.scopes.isEmpty() -> {
                UseCaseOutcome.Failure(
                    errorCode = CreateApiClientError.NO_SCOPE,
                    errorMessage = "An API client needs at least one client scope"
                )
            }

            unknownScopes.isNotEmpty() -> {
                UseCaseOutcome.Failure(
                    errorCode = CreateApiClientError.UNKNOWN_SCOPE,
                    errorMessage = "These client scopes cannot be given to an API client: $unknownScopes"
                )
            }

            else -> {
                null
            }
        }
    }

    private fun clientIdFor(name: String): String {
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
}
