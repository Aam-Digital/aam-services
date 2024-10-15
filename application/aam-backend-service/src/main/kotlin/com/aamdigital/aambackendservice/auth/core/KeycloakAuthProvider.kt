package com.aamdigital.aambackendservice.auth.core

import com.aamdigital.aambackendservice.error.AamErrorCode
import com.aamdigital.aambackendservice.error.ExternalSystemException
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient

data class KeycloakTokenResponse(
    @JsonProperty("access_token") val accessToken: String,
)

/**
 * This class is an implementation of the AuthProvider interface and is responsible for
 * fetching tokens from a Keycloak authentication server. Used for accessing
 * third party systems and services.
 *
 * Not related to authentication mechanics related to endpoints we provide to others.
 *
 * @property httpClient The RestClient used for making HTTP requests.
 * @property objectMapper The ObjectMapper used for parsing JSON responses.
 */
class KeycloakAuthProvider(
    val httpClient: RestClient,
    val objectMapper: ObjectMapper,
) : AuthProvider {

    enum class KeycloakAuthProviderError : AamErrorCode {
        EMPTY_RESPONSE,
        RESPONSE_PARSING_ERROR,
    }

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun fetchToken(authClientConfig: AuthConfig): TokenResponse {
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

        return try {
            val response = httpClient.post()
                .uri(authClientConfig.tokenEndpoint)
                .headers {
                    it.contentType = MediaType.APPLICATION_FORM_URLENCODED
                }
                .body(
                    formData
                )
                .retrieve()
                .body(String::class.java)
            parseResponse(response)
        } catch (ex: ExternalSystemException) {
            logger.error(ex.message, ex)
            throw ex
        }
    }

    private fun parseResponse(raw: String?): TokenResponse {
        if (raw.isNullOrEmpty()) {
            throw ExternalSystemException(
                message = "Could not parse access token from KeycloakAuthProvider.",
                code = KeycloakAuthProviderError.EMPTY_RESPONSE
            )
        }

        try {
            val keycloakTokenResponse = objectMapper.readValue(raw, KeycloakTokenResponse::class.java)
            return TokenResponse(
                token = keycloakTokenResponse.accessToken
            )
        } catch (ex: Exception) {
            throw ExternalSystemException(
                message = "Could not parse access token from KeycloakAuthProvider",
                cause = ex,
                code = KeycloakAuthProviderError.RESPONSE_PARSING_ERROR
            )
        }
    }
}
