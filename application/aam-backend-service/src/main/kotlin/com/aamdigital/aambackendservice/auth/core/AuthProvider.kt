package com.aamdigital.aambackendservice.auth.core

import reactor.core.publisher.Mono

data class TokenResponse(val token: String)

data class AuthConfig(
    val clientId: String,
    val clientSecret: String,
    val tokenEndpoint: String,
    val grantType: String,
    val scope: String,
)

interface AuthProvider {
    fun fetchToken(authClientConfig: AuthConfig): Mono<TokenResponse>
}
