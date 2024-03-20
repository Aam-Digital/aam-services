package com.aamdigital.aambackendservice.e2e

import com.aamdigital.aambackendservice.common.AuthTestingService
import com.aamdigital.aambackendservice.common.CouchDbTestingService
import com.aamdigital.aambackendservice.container.TestContainers
import com.aamdigital.aambackendservice.container.TestContainers.CONTAINER_COUCHDB
import com.aamdigital.aambackendservice.container.TestContainers.CONTAINER_KEYCLOAK
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.context.ImportTestcontainers
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.test.context.ActiveProfiles
import org.springframework.web.client.RestTemplate

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT
)
@ActiveProfiles("e2e")
@ImportTestcontainers(TestContainers::class)
abstract class SpringIntegrationTest {

    companion object {
        private const val APPLICATION_PORT = 9000

        protected val REST_TEMPLATE: RestTemplate = RestTemplateBuilder()
            .rootUri("http://localhost:$APPLICATION_PORT")
            .build()
    }

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

    var latestResponseBody: Any? = null
    var latestResponse: Any? = null
    var authToken: String? = null

    fun getObjectNode(url: String) {
        val headers = HttpHeaders()

        if (authToken != null) {
            headers.set(HttpHeaders.AUTHORIZATION, "Bearer $authToken")
        }

        val requestEntity = HttpEntity(null, headers)

        REST_TEMPLATE.exchange(
            url,
            HttpMethod.GET,
            requestEntity,
            ObjectNode::class.java,
        ).let {
            latestResponse = it
            latestResponseBody = it.body
        }
    }

    fun getArrayNode(url: String) {
        val headers = HttpHeaders()

        if (authToken != null) {
            headers.set(HttpHeaders.AUTHORIZATION, "Bearer $authToken")
        }

        val requestEntity = HttpEntity(null, headers)

        REST_TEMPLATE.exchange(
            url,
            HttpMethod.GET,
            requestEntity,
            ArrayNode::class.java,
        ).let {
            latestResponse = it
            latestResponseBody = it.body
        }
    }

    fun fetchToken(client: String, secret: String, realm: String) {
        authToken = authTestingService.fetchToken(client, secret, realm)
    }
}
