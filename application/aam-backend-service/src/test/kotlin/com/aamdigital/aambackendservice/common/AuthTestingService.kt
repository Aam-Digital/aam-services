package com.aamdigital.aambackendservice.common

import com.fasterxml.jackson.databind.node.ObjectNode
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import org.springframework.web.client.RestTemplate

class AuthTestingService(
    private val restTemplate: RestTemplate
) {
    /**
     * Token of an API client (client credentials flow).
     * No scope is requested, so the token carries exactly the client's Default client scopes.
     */
    fun fetchToken(
        client: String,
        secret: String,
        realm: String
    ): String? =
        requestToken(
            realm = realm,
            parameters =
                mapOf(
                    "client_id" to client,
                    "client_secret" to secret,
                    "grant_type" to "client_credentials"
                )
        )

    /**
     * Token of a user logging in through a public client (resource owner password flow),
     * like a user of the frontend app.
     */
    fun fetchUserToken(
        client: String,
        username: String,
        password: String,
        realm: String
    ): String? =
        requestToken(
            realm = realm,
            parameters =
                mapOf(
                    "client_id" to client,
                    "username" to username,
                    "password" to password,
                    "grant_type" to "password"
                )
        )

    private fun requestToken(
        realm: String,
        parameters: Map<String, String>
    ): String? {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_FORM_URLENCODED

        val body: MultiValueMap<String, String> = LinkedMultiValueMap()
        parameters.forEach { (name, value) -> body.add(name, value) }

        val requestEntity = HttpEntity(body, headers)

        val tokenResponse =
            restTemplate.exchange(
                "/realms/$realm/protocol/openid-connect/token",
                HttpMethod.POST,
                requestEntity,
                ObjectNode::class.java
            )

        return tokenResponse.body?.get("access_token")?.textValue()
    }
}
