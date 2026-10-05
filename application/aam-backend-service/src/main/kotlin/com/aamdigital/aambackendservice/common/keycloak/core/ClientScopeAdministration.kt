package com.aamdigital.aambackendservice.common.keycloak.core

/**
 * A client scope defined in the Keycloak realm.
 *
 * @property id internal Keycloak id
 * @property includedInTokenScope whether Keycloak adds this scope to the `scope` claim of access tokens
 */
data class KeycloakClientScope(
    val id: String,
    val name: String,
    val includedInTokenScope: Boolean
)

/**
 * A Keycloak client with an enabled service account, i.e. an API client using the client credentials flow.
 *
 * @property id internal Keycloak id
 * @property clientId the id used by the client to authenticate
 * @property defaultClientScopes names of the scopes that are always included in the client's tokens
 * @property optionalClientScopes names of the scopes that are only included if requested explicitly
 */
data class KeycloakServiceAccountClient(
    val id: String,
    val clientId: String,
    val defaultClientScopes: Set<String>,
    val optionalClientScopes: Set<String>
)

/**
 * The Keycloak realm administration needed to manage the client scopes of API clients.
 */
interface ClientScopeAdministration {
    fun findClientScopes(): List<KeycloakClientScope>

    fun createClientScope(
        name: String,
        description: String
    )

    fun findServiceAccountClients(): List<KeycloakServiceAccountClient>

    /**
     * Make the scope a Default scope of the client, replacing an Optional assignment of it.
     */
    fun assignDefaultClientScope(
        client: KeycloakServiceAccountClient,
        scope: KeycloakClientScope
    )
}
