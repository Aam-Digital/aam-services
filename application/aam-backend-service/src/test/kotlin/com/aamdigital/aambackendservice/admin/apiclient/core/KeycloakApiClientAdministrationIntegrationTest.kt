package com.aamdigital.aambackendservice.admin.apiclient.core

import com.aamdigital.aambackendservice.common.AuthTestingService
import com.aamdigital.aambackendservice.common.error.NotFoundException
import com.aamdigital.aambackendservice.common.keycloak.di.AamKeycloakConfig
import com.aamdigital.aambackendservice.common.keycloak.di.KeycloakAdminConfiguration
import com.aamdigital.aambackendservice.container.TestContainers
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.keycloak.admin.client.CreatedResponseUtil
import org.keycloak.admin.client.Keycloak
import org.keycloak.representations.idm.ClientRepresentation
import org.springframework.boot.restclient.RestTemplateBuilder
import org.springframework.web.client.HttpClientErrorException
import java.util.Base64
import java.util.Random

/**
 * Proves against a real Keycloak that the backend, signed in with the `aam-backend` client as imported from
 * `aam-backend-client.json`, can manage API clients, and that it leaves every other client of the realm alone.
 */
class KeycloakApiClientAdministrationIntegrationTest {
    companion object {
        private const val REALM = TestContainers.KEYCLOAK_REALM
        private const val SUFFIX_LENGTH = 6

        @BeforeAll
        @JvmStatic
        fun startKeycloak() {
            TestContainers.startKeycloak()
        }
    }

    private val openedClients = mutableListOf<Keycloak>()
    private val createdClientIds = mutableListOf<String>()
    private lateinit var backend: Keycloak
    private lateinit var administration: KeycloakApiClientAdministration

    private val keycloakUrl get() = TestContainers.CONTAINER_KEYCLOAK.authServerUrl.trimEnd('/')

    private val authTestingService get() = AuthTestingService(RestTemplateBuilder().rootUri(keycloakUrl).build())

    @BeforeEach
    fun setUp() {
        backend =
            KeycloakAdminConfiguration()
                .keycloak(
                    AamKeycloakConfig(
                        serverUrl = keycloakUrl,
                        realm = REALM,
                        clientId = "aam-backend",
                        clientSecret = TestContainers.AAM_BACKEND_CLIENT_SECRET
                    )
                ).also { openedClients += it }
        administration =
            KeycloakApiClientAdministration(
                keycloak = backend,
                realm = REALM,
                backendClientId = "aam-backend",
                // no_such_scope is offered, but does not exist in the realm
                offeredScopes =
                    setOf(
                        "reporting_read",
                        "reporting_write",
                        "third_party_authentication",
                        "no_such_scope"
                    )
            )
    }

    @AfterEach
    fun cleanUp() {
        val clients = admin().realm(REALM).clients()
        createdClientIds.forEach { clientId ->
            clients.findByClientId(clientId).forEach { clients.get(it.id).remove() }
        }
        openedClients.forEach { it.close() }
    }

    /** Keycloak as the administrator of the realm sees it. */
    private fun admin(): Keycloak = TestContainers.CONTAINER_KEYCLOAK.keycloakAdminClient.also { openedClients += it }

    private fun create(
        name: String,
        scopes: Set<String> = setOf("reporting_read")
    ) = administration
        .createApiClient(name = name, scopes = scopes, createdBy = "manager@example.com")
        .also { createdClientIds += it.clientId }

    @Test
    fun `should create an API client whose access tokens carry its client scopes`() {
        // When
        val credentials = create("test tokens", setOf("reporting_read", "third_party_authentication"))

        // Then the external system can get tokens with the client credentials flow
        val token = authTestingService.fetchToken(credentials.clientId, credentials.clientSecret, REALM)
        val claims = claimsOf(token)
        assertThat(claims.get("scope").textValue().split(" "))
            .contains("reporting_read", "third_party_authentication")
            .doesNotContain("reporting_write")
        // e.g. the webhooks of the reporting API are owned by the subject of the token
        assertThat(claims.get("sub")?.textValue()).isNotBlank()
        // and no realm role (like the realm's default roles of the service account) that a role-based check accepts
        assertThat(claims.path("realm_access").path("roles").map { it.textValue() }).isEmpty()
        assertThat(claims.path("resource_access").properties()).isEmpty()
    }

    @Test
    fun `should create an API client that cannot request any other client scope`() {
        // Given client scopes that the realm gives every new client as Optional scopes (like reporting_write in
        // the test realm), which a client can request explicitly
        val realm = admin().realm(REALM)
        assertThat(realm.defaultOptionalClientScopes.map { it.name }).contains("reporting_write")

        // When
        val credentials = create("test optional", setOf("reporting_read"))

        // Then
        val client = realm.clients().findByClientId(credentials.clientId).single()
        val clientResource = realm.clients().get(client.id)
        assertThat(clientResource.defaultClientScopes.map { it.name }).contains("reporting_read")
        assertThat(clientResource.optionalClientScopes).isEmpty()
    }

    @Test
    fun `should create an API client that can only use the client credentials flow`() {
        // When
        val credentials = create("test flows")

        // Then
        val client =
            admin()
                .realm(REALM)
                .clients()
                .findByClientId(credentials.clientId)
                .single()
        assertThat(client.isPublicClient).isFalse()
        assertThat(client.isServiceAccountsEnabled).isTrue()
        assertThat(client.isStandardFlowEnabled).isFalse()
        assertThat(client.isImplicitFlowEnabled).isFalse()
        assertThat(client.isDirectAccessGrantsEnabled).isFalse()
        assertThat(client.redirectUris).isEmpty()
    }

    @Test
    fun `should list the API clients with their client scopes and creator`() {
        // Given
        val created = create("Test system", setOf("reporting_read", "reporting_write"))

        // When
        val apiClient = administration.findApiClients().single { it.clientId == created.clientId }

        // Then
        assertThat(apiClient.clientId).startsWith("api-test-system-")
        assertThat(apiClient.name).isEqualTo("Test system")
        assertThat(apiClient.scopes).contains("reporting_read", "reporting_write")
        assertThat(apiClient.createdBy).isEqualTo("manager@example.com")
        assertThat(apiClient.createdAt).isNotBlank()
    }

    @Test
    fun `should replace the secret of an API client, so that the previous one stops working`() {
        // Given
        val previous = create("test secret")

        // When
        val regenerated = administration.regenerateSecret(previous.clientId)

        // Then
        assertThat(regenerated.clientSecret).isNotEqualTo(previous.clientSecret)
        assertThat(authTestingService.fetchToken(regenerated.clientId, regenerated.clientSecret, REALM)).isNotBlank()
        assertThatThrownBy { authTestingService.fetchToken(previous.clientId, previous.clientSecret, REALM) }
            .isInstanceOf(HttpClientErrorException::class.java)
    }

    @Test
    fun `should delete an API client`() {
        // Given
        val created = create("test delete")

        // When
        administration.deleteApiClient(created.clientId)

        // Then
        assertThat(admin().realm(REALM).clients().findByClientId(created.clientId)).isEmpty()
        assertThat(administration.findApiClients().map { it.clientId }).doesNotContain(created.clientId)
    }

    @Test
    fun `should neither list nor change nor delete any other client of the realm`() {
        // Given a client that looks like an API client, but was set up in the Keycloak admin console
        val clients = admin().realm(REALM).clients()
        val manualClient =
            ClientRepresentation().apply {
                clientId = "api-test-manual"
                isServiceAccountsEnabled = true
                isPublicClient = false
            }
        clients.create(manualClient).use { CreatedResponseUtil.getCreatedId(it) }
        createdClientIds += "api-test-manual"

        // Then
        assertThat(administration.findApiClients().map { it.clientId })
            .doesNotContain("api-test-manual", "app", "aam-backend", "dummy-client")
        listOf("app", "aam-backend", "api-test-manual").forEach { clientId ->
            assertThatThrownBy { administration.deleteApiClient(clientId) }
                .isInstanceOf(NotFoundException::class.java)
                .extracting("code")
                .isEqualTo(ApiClientAdministrationError.API_CLIENT_NOT_FOUND)
            assertThatThrownBy { administration.regenerateSecret(clientId) }
                .isInstanceOf(NotFoundException::class.java)
            assertThat(clients.findByClientId(clientId)).hasSize(1)
        }
    }

    @Test
    fun `should create an API client with the client id the user chose`() {
        // When
        val credentials =
            administration
                .createApiClient("Chosen", setOf("reporting_read"), "manager@example.com", "api-test-chosen")
                .also { createdClientIds += it.clientId }

        // Then
        assertThat(credentials.clientId).isEqualTo("api-test-chosen")
        assertThat(administration.findApiClients().map { it.clientId }).contains("api-test-chosen")
    }

    @Test
    fun `should report a client id the user chose that exists already`() {
        // Given
        administration
            .createApiClient("Chosen", setOf("reporting_read"), "manager@example.com", "api-test-taken")
            .also { createdClientIds += it.clientId }

        // Then
        assertThatThrownBy {
            administration.createApiClient("Other", setOf("reporting_read"), "manager@example.com", "api-test-taken")
        }.extracting("code").isEqualTo(ApiClientAdministrationError.API_CLIENT_ALREADY_EXISTS)
    }

    @Test
    fun `should try another client id if the generated one exists already`() {
        // Given an API client with the client id that is generated first
        // (a random source that gives "aaaaaa" as first suffix and "bbbbbb" as second one)
        val suffixes = ArrayDeque(List(SUFFIX_LENGTH) { 0 } + List(SUFFIX_LENGTH) { 1 })
        val random =
            object : Random() {
                override fun nextInt(bound: Int) = suffixes.removeFirstOrNull() ?: 2
            }
        val administrationWithCollision =
            KeycloakApiClientAdministration(
                keycloak = backend,
                realm = REALM,
                backendClientId = "aam-backend",
                offeredScopes = setOf("reporting_read"),
                random = random
            )
        administration
            .createApiClient("Collision", setOf("reporting_read"), null, "api-collision-aaaaaa")
            .also { createdClientIds += it.clientId }

        // When
        val credentials =
            administrationWithCollision
                .createApiClient("Collision", setOf("reporting_read"), null)
                .also { createdClientIds += it.clientId }

        // Then
        assertThat(credentials.clientId).isEqualTo("api-collision-bbbbbb")
    }

    @Test
    fun `should not leave a client behind if a client scope does not exist`() {
        // When
        assertThatThrownBy { create("test missing scope", setOf("reporting_read", "no_such_scope")) }
            .isInstanceOf(NotFoundException::class.java)
            .extracting("code")
            .isEqualTo(ApiClientAdministrationError.CLIENT_SCOPE_NOT_FOUND)

        // Then
        assertThat(administration.findApiClients().map { it.name }).doesNotContain("test missing scope")
        assertThat(
            admin()
                .realm(REALM)
                .clients()
                .findAll()
                .map { it.name }
        ).doesNotContain("test missing scope")
    }

    private fun claimsOf(accessToken: String?): JsonNode {
        val payload = checkNotNull(accessToken).split(".")[1]
        return jacksonObjectMapper().readTree(Base64.getUrlDecoder().decode(payload))
    }
}
