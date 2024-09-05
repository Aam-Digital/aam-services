package com.aamdigital.aambackendservice.auth.core

import com.aamdigital.aambackendservice.error.ExternalSystemException
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

data class KeycloakTokenResponse(
    @JsonProperty("access_token") val accessToken: String,
)

class KeycloakAuthProvider(
    val webClient: WebClient,
    val objectMapper: ObjectMapper,
) : AuthProvider {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun fetchToken(authClientConfig: AuthConfig): Mono<TokenResponse> {
        val formData = LinkedMultiValueMap(
            mutableMapOf(
                "client_id" to listOf(authClientConfig.clientId),
                "client_secret" to listOf(authClientConfig.clientSecret),
                "grant_type" to listOf(authClientConfig.grantType),
            ).also {
                if (authClientConfig.scope.isNotBlank()) {
                    "scope" to listOf(authClientConfig.scope)
                }
            }
        )

        return webClient.post()
            .uri(authClientConfig.tokenEndpoint)
            .headers {
                it.contentType = MediaType.APPLICATION_FORM_URLENCODED
            }
            .body(
                BodyInserters.fromFormData(
                    formData
                )
            ).exchangeToMono {
                it.bodyToMono(String::class.java)
            }.map {
                parseResponse(it)
            }.doOnError { logger.error(it.message, it) }
    }

    private fun parseResponse(raw: String): TokenResponse {
        try {
            val keycloakTokenResponse = objectMapper.readValue(raw, KeycloakTokenResponse::class.java)
            return TokenResponse(
                token = keycloakTokenResponse.accessToken
            )
        } catch (e: Exception) {
            throw ExternalSystemException("Could not parse access token from KeycloakAuthProvider", e)
        }
    }
}
