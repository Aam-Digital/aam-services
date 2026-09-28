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
     */
    const val LEGACY_PROVIDER_ROLE = "third-party-authentication-provider"
}
