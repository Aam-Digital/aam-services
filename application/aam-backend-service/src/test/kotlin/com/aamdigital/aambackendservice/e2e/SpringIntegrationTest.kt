package com.aamdigital.aambackendservice.e2e

import com.aamdigital.aambackendservice.common.AuthTestingService
import com.aamdigital.aambackendservice.common.CouchDbTestingService
import com.aamdigital.aambackendservice.container.TestContainers
import com.aamdigital.aambackendservice.container.TestContainers.CONTAINER_COUCHDB
import com.aamdigital.aambackendservice.container.TestContainers.CONTAINER_KEYCLOAK
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.context.ImportTestcontainers
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatusCode
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT
)
@ActiveProfiles("e2e")
@ImportTestcontainers(TestContainers::class)
@ContextConfiguration
abstract class SpringIntegrationTest {

    companion object {
        private const val APPLICATION_PORT = 9000
    }

    val objectMapper = jacksonObjectMapper()

    val restTemplate: RestTemplate = RestTemplateBuilder()
        .rootUri("http://localhost:$APPLICATION_PORT")
        .build()

    internal val couchDbTestingService: CouchDbTestingService = CouchDbTestingService(
        RestTemplateBuilder()
            .rootUri("http://localhost:${CONTAINER_COUCHDB.getMappedPort(5984)}")
            .basicAuthentication("admin", "docker")
            .build()
    )

    internal val authTestingService: AuthTestingService = AuthTestingService(
        RestTemplateBuilder()
            .rootUri("http://localhost:${CONTAINER_KEYCLOAK.getMappedPort(8080)}")
            .basicAuthentication("admin", "docker")
            .build()
    )

    var latestResponseBody: String? = null
    var latestResponseStatus: HttpStatusCode? = null
    var authToken: String? = null

    fun exchange(url: String, method: HttpMethod, body: String? = null) {
        val headers = HttpHeaders()

        if (authToken != null) {
            headers.set(HttpHeaders.AUTHORIZATION, "Bearer $authToken")
        }

        val requestEntity = HttpEntity(body, headers)

        try {
            restTemplate.exchange(
                url,
                method,
                requestEntity,
                String::class.java,
            ).let {
                latestResponseStatus = it.statusCode
                latestResponseBody = it.body
            }
        } catch (ex: HttpClientErrorException) {
            latestResponseStatus = ex.statusCode
            latestResponseBody = ex.responseBodyAsString
        }
    }

    fun parseBodyToObjectNode(): ObjectNode? {
        return latestResponseBody?.let {
            objectMapper.readValue<ObjectNode>(it)
        }
    }

    fun parseBodyToArrayNode(): ArrayNode? {
        return latestResponseBody?.let {
            objectMapper.readValue<ArrayNode>(it)
        }
    }

    fun fetchToken(client: String, secret: String, realm: String) {
        authToken = authTestingService.fetchToken(client, secret, realm)
    }
}
