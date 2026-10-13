package com.aamdigital.aambackendservice.admin.apiclient.core

import com.aamdigital.aambackendservice.admin.apiclient.domain.ApiClient
import com.aamdigital.aambackendservice.admin.apiclient.domain.ApiClientCredentials
import com.aamdigital.aambackendservice.common.error.AamErrorCode

enum class ApiClientAdministrationError : AamErrorCode {
    API_CLIENT_NOT_FOUND,
    API_CLIENT_ALREADY_EXISTS,
    CLIENT_SCOPE_NOT_FOUND,
    MISSING_KEYCLOAK_PERMISSION,
    KEYCLOAK_REQUEST_FAILED
}

/**
 * An [ApiClient] to be created.
 *
 * @property clientId has to start with [ApiClientAdministration.CLIENT_ID_PREFIX]
 * @property scopes client scopes to assign as Default scopes; they have to exist in the realm
 */
data class NewApiClient(
    val clientId: String,
    val name: String,
    val scopes: Set<String>,
    val createdBy: String?
)

/**
 * The administration of [ApiClient]s in the Keycloak realm.
 *
 * Only clients created through [createApiClient] are found, changed or deleted, never any other client of the
 * realm (like the client of the app itself or the backend's own client), even when asked for by their client id.
 * Implementations throw an `AamException` with an [ApiClientAdministrationError] code if a request fails.
 */
interface ApiClientAdministration {
    companion object {
        /** Prefix of the client id of every [ApiClient], to tell them apart from other clients in the realm. */
        const val CLIENT_ID_PREFIX = "api-"
    }

    fun findApiClients(): List<ApiClient>

    /**
     * Creates a confidential client that can only use the client credentials flow.
     *
     * @return the credentials, which are not stored anywhere else and cannot be read again through this interface
     */
    fun createApiClient(newApiClient: NewApiClient): ApiClientCredentials

    /**
     * Replaces the secret of the client, so that the previous one stops working immediately.
     */
    fun regenerateSecret(clientId: String): ApiClientCredentials

    fun deleteApiClient(clientId: String)
}
