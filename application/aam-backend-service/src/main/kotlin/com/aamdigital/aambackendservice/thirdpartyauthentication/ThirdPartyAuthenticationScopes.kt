package com.aamdigital.aambackendservice.thirdpartyauthentication

/**
 * Keycloak client scope granting an external system the right to create user sessions.
 */
object ThirdPartyAuthenticationScopes {
    const val SESSION_PROVIDER = "third_party_authentication"

    /**
     * Realm role that granted this access before the client scope existed.
     * On startup, clients whose service account holds it are given the [SESSION_PROVIDER] scope,
     * and the role is deleted afterward (see ClientScopeRequest.replacesRealmRole).
     * Until then, the role is still accepted, so that a migration that cannot run (e.g. missing Keycloak
     * permissions) does not lock out the external system.
     */
    const val LEGACY_PROVIDER_ROLE = "third-party-authentication-provider"
}
