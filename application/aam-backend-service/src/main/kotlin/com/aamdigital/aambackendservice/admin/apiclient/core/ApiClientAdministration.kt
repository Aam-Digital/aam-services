package com.aamdigital.aambackendservice.admin.apiclient.core

import com.aamdigital.aambackendservice.admin.apiclient.domain.ApiClient
import com.aamdigital.aambackendservice.admin.apiclient.domain.ApiClientCredentials
import com.aamdigital.aambackendservice.common.error.AamErrorCode

enum class ApiClientAdministrationError : AamErrorCode {
    INVALID_NAME,
    INVALID_CLIENT_ID,
    NO_SCOPE,
    UNKNOWN_SCOPE,
    API_CLIENT_NOT_FOUND,
    API_CLIENT_ALREADY_EXISTS,
    CLIENT_SCOPE_NOT_FOUND,
    MISSING_KEYCLOAK_PERMISSION,
    KEYCLOAK_REQUEST_FAILED
}

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
     * @param name label of the API client, e.g. the name of the external system
     * @param scopes client scopes to give the API client; only those offered to API clients
     * @param createdBy the user creating the API client
     * @param clientId the client id chosen by the user (starting with [CLIENT_ID_PREFIX]); if null, one is generated
     *  from the name
     * @return the credentials, which are not stored anywhere else and cannot be read again through this interface
     */
    fun createApiClient(
        name: String,
        scopes: Set<String>,
        createdBy: String?,
        clientId: String? = null
    ): ApiClientCredentials

    /**
     * Replaces the secret of the client, so that the previous one stops working immediately.
     */
    fun regenerateSecret(clientId: String): ApiClientCredentials

    fun deleteApiClient(clientId: String)
}
