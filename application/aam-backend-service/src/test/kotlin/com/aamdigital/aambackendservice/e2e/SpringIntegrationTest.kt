package com.aamdigital.aambackendservice.e2e

import com.aamdigital.aambackendservice.common.AuthTestingService
import com.aamdigital.aambackendservice.common.CouchDbTestingService
import com.aamdigital.aambackendservice.container.TestContainers
import com.aamdigital.aambackendservice.container.TestContainers.CONTAINER_COUCHDB
import com.aamdigital.aambackendservice.container.TestContainers.CONTAINER_KEYCLOAK
import com.aamdigital.aambackendservice.e2e.contract.OpenApiContractValidators
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.context.ImportTestcontainers
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.core.io.Resource
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.RestTemplate

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT
)
@ActiveProfiles("e2e")
@ImportTestcontainers(TestContainers::class)
@ContextConfiguration(initializers = [FirebaseTestCredentialInitializer::class])
abstract class SpringIntegrationTest {
    companion object {
        private const val APPLICATION_PORT = 9000
    }

    val objectMapper = jacksonObjectMapper()

    val restTemplate: RestTemplate =
        RestTemplateBuilder()
            .rootUri("http://localhost:$APPLICATION_PORT")
            .build()

    internal val couchDbTestingService: CouchDbTestingService =
        CouchDbTestingService(
            RestTemplateBuilder()
                .rootUri("http://localhost:${CONTAINER_COUCHDB.getMappedPort(5984)}")
                .basicAuthentication("admin", "docker")
                .build()
        )

    internal val authTestingService: AuthTestingService =
        AuthTestingService(
            RestTemplateBuilder()
                .rootUri("http://localhost:${CONTAINER_KEYCLOAK.getMappedPort(8080)}")
                .basicAuthentication("admin", "docker")
                .build()
        )

    var latestResponseBody: String? = null
    var latestResponseHeaders: HttpHeaders? = null
    var latestResponseStatus: HttpStatusCode? = null
    var authToken: String? = null

    fun exchange(
        url: String,
        method: HttpMethod,
        body: String? = null
    ) {
        val headers = HttpHeaders()

        if (authToken != null) {
            headers.set(HttpHeaders.AUTHORIZATION, "Bearer $authToken")
        }

        headers.contentType = MediaType.APPLICATION_JSON
        headers.accept = listOf(MediaType.APPLICATION_JSON)

        val requestEntity = HttpEntity(body, headers)

        try {
            restTemplate
                .exchange(
                    url,
                    method,
                    requestEntity,
                    String::class.java
                ).let {
                    latestResponseStatus = it.statusCode
                    latestResponseBody = it.body
                    latestResponseHeaders = it.headers
                }
        } catch (ex: HttpStatusCodeException) {
            latestResponseStatus = ex.statusCode
            latestResponseBody = ex.responseBodyAsString
            latestResponseHeaders = ex.responseHeaders
        }

        validateContract(method, url, body)
    }

    fun exchangeMultipart(
        url: String,
        file: Resource
    ) {
        val headers = HttpHeaders()

        if (authToken != null) {
            headers.set(HttpHeaders.AUTHORIZATION, "Bearer $authToken")
        }

        headers.contentType = MediaType.MULTIPART_FORM_DATA
        headers.accept = listOf(MediaType.APPLICATION_JSON)

        val builder = MultipartBodyBuilder()
        builder.part("template", file)

        val requestEntity = HttpEntity(builder.build(), headers)

        try {
            restTemplate
                .postForEntity(
                    url,
                    requestEntity,
                    String::class.java
                ).let {
                    latestResponseStatus = it.statusCode
                    latestResponseBody = it.body
                    latestResponseHeaders = it.headers
                }
        } catch (ex: HttpStatusCodeException) {
            latestResponseStatus = ex.statusCode
            latestResponseBody = ex.responseBodyAsString
            latestResponseHeaders = ex.responseHeaders
        }

        // Forward the multipart form parts to the contract validator. The validator parses
        // form bodies with its URL-encoded parser, so we hand it a URL-encoded *representation*
        // of the parts (not the literal multipart bytes) — enough for it to see the documented
        // `template` part and stop reporting a false "request body is required but none found".
        // Keep this in sync with the parts added above if more form fields are ever sent.
        val multipartRepresentation = "template=${file.filename ?: "file"}"
        validateContract(HttpMethod.POST, url, multipartRepresentation, MediaType.MULTIPART_FORM_DATA_VALUE)
    }

    /** GET a binary/streaming endpoint, accepting `application/octet-stream`. */
    fun download(url: String) {
        val headers = HttpHeaders()

        if (authToken != null) {
            headers.set(HttpHeaders.AUTHORIZATION, "Bearer $authToken")
        }

        headers.accept = listOf(MediaType.APPLICATION_OCTET_STREAM, MediaType.ALL)

        val requestEntity = HttpEntity<Void>(headers)

        try {
            restTemplate
                .exchange(
                    url,
                    HttpMethod.GET,
                    requestEntity,
                    ByteArray::class.java
                ).let {
                    latestResponseStatus = it.statusCode
                    latestResponseBody = it.body?.let { bytes -> String(bytes) }
                    latestResponseHeaders = it.headers
                }
        } catch (ex: HttpStatusCodeException) {
            latestResponseStatus = ex.statusCode
            latestResponseBody = ex.responseBodyAsString
            latestResponseHeaders = ex.responseHeaders
        }

        validateContract(HttpMethod.GET, url, null)
    }

    private fun validateContract(
        method: HttpMethod,
        url: String,
        requestBody: String?,
        requestContentType: String? = null
    ) {
        OpenApiContractValidators.validate(
            method = method.name(),
            path = url,
            requestBody = requestBody,
            requestContentType = requestContentType,
            statusCode = latestResponseStatus?.value() ?: return,
            responseBody = latestResponseBody,
            responseContentType = latestResponseHeaders?.contentType?.toString()
        )
    }

    fun parseBodyToObjectNode(): ObjectNode? =
        latestResponseBody?.let {
            objectMapper.readValue<ObjectNode>(it)
        }

    fun parseHeader(name: String): List<String> = latestResponseHeaders?.getOrElse(name) { emptyList() } ?: emptyList()

    fun parseBodyToArrayNode(): ArrayNode? =
        latestResponseBody?.let {
            objectMapper.readValue<ArrayNode>(it)
        }

    fun fetchToken(
        client: String,
        secret: String,
        realm: String
    ) {
        authToken = authTestingService.fetchToken(client, secret, realm)
    }
}
