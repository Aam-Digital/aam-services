package com.aamdigital.aambackendservice.common.auth.core

data class TokenResponse(val token: String)


/**
 * Configuration data class for handling authentication.
 *
 * @property clientId The client ID used for authentication.
 * @property clientSecret The client secret used for authentication.
 * @property tokenEndpoint The endpoint URL to request the token.
 * @property grantType The OAuth grant type to be used.
 * @property scope The scope of the access request.
 */
data class AuthConfig(
    val clientId: String,
    val clientSecret: String,
    val tokenEndpoint: String,
    val grantType: String,
    val scope: String,
)

/**
 * Interface representing an authentication provider responsible for fetching an authentication token.
 *
 * Used for fetching access tokens for third party systems.
 */
interface AuthProvider {
    fun fetchToken(authClientConfig: AuthConfig): TokenResponse
}
