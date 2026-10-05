package com.aamdigital.aambackendservice.common.keycloak.core

/**
 * A Keycloak client scope that a feature module requires to authorize API clients.
 * Feature modules register these as beans, and [ClientScopeInitializer] makes sure on startup
 * that they exist in the realm (similar to how a DatabaseRequest ensures a CouchDB database).
 *
 * The initializer never assigns a scope to a client on its own, except for the promotion requested here.
 *
 * @property name of the client scope, as it appears in the `scope` claim of an access token
 * @property description shown in the Keycloak admin console
 * @property promoteOptionalToDefault make the scope a Default scope of every API client (service account)
 *  that only has it as an Optional scope, so that it is included in tokens without explicitly requesting it
 */
data class ClientScopeRequest(
    val name: String,
    val description: String,
    val promoteOptionalToDefault: Boolean = false
)
