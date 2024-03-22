package com.aamdigital.aambackendservice.common

import com.fasterxml.jackson.databind.node.ObjectNode
import org.slf4j.LoggerFactory
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import org.springframework.web.client.RestTemplate

class AuthTestingService(
    private val restTemplate: RestTemplate,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun fetchToken(client: String, secret: String, realm: String): String? {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_FORM_URLENCODED

        val body: MultiValueMap<String, String> = LinkedMultiValueMap()
        body.add("client_id", client)
        body.add("client_secret", secret)
        body.add("grant_type", "client_credentials")
        body.add("scope", "reporting_read reporting_write")

        val requestEntity = HttpEntity(body, headers)

        val tokenResponse = restTemplate.exchange(
            "/realms/$realm/protocol/openid-connect/token",
            HttpMethod.POST,
            requestEntity,
            ObjectNode::class.java
        )

        return tokenResponse.body?.get("access_token")?.textValue()
    }
}
