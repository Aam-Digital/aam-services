package com.aamdigital.aambackendservice.common.keycloak

import com.aamdigital.aambackendservice.common.AuthTestingService
import com.aamdigital.aambackendservice.common.keycloak.core.ClientScopeInitializer
import com.aamdigital.aambackendservice.common.keycloak.core.ClientScopeRequest
import com.aamdigital.aambackendservice.common.keycloak.core.KeycloakClientScopeAdministration
import com.aamdigital.aambackendservice.common.keycloak.di.AamKeycloakConfig
import com.aamdigital.aambackendservice.common.keycloak.di.KeycloakAdminConfiguration
import com.aamdigital.aambackendservice.container.KeycloakClientDefinitions
import com.aamdigital.aambackendservice.container.TestContainers
import com.aamdigital.aambackendservice.notification.core.create.email.KeycloakUserEmailProvider
import com.aamdigital.aambackendservice.thirdpartyauthentication.core.AamKeycloakAuthenticationProvider
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.keycloak.admin.client.CreatedResponseUtil
import org.keycloak.admin.client.Keycloak
import org.keycloak.admin.client.resource.ClientsResource
import org.keycloak.representations.idm.ClientRepresentation
import org.springframework.boot.restclient.RestTemplateBuilder
import java.util.Base64

/**
 * Proves, against a real Keycloak, that the client definitions that ship with the application
 * (`keycloak/`, see its README) work: the `aam-backend` client that the Keycloak container is started with is
 * imported from `aam-backend-client.json` (see [TestContainers.startKeycloak]), and everything the services do
 * with it has to succeed with exactly the permissions that file grants.
 *
 * The code under test is the real one, signed in the way the application signs in. A role that the definition
 * lacks makes one of these tests fail, instead of a deployment.
 */
class KeycloakClientDefinitionsIntegrationTest {
    companion object {
        private const val REALM = TestContainers.KEYCLOAK_REALM
        private const val SCOPE_NAME = "definition_test_scope"
        private const val API_CLIENT_ID = "definition-test-api-client"

        @BeforeAll
        @JvmStatic
        fun startKeycloak() {
            TestContainers.startKeycloak()
        }
    }

    private val openedClients = mutableListOf<Keycloak>()

    private val keycloakUrl get() = TestContainers.CONTAINER_KEYCLOAK.authServerUrl.trimEnd('/')

    private val backendConfig
        get() =
            AamKeycloakConfig(
                serverUrl = keycloakUrl,
                realm = REALM,
                clientId = "aam-backend",
                clientSecret = TestContainers.AAM_BACKEND_CLIENT_SECRET
            )

    @AfterEach
    fun closeKeycloakClients() {
        openedClients.forEach { it.close() }
    }

    /** Keycloak as the administrator of the realm sees it, to set up and clean up what the tests create. */
    private fun admin(): Keycloak = TestContainers.CONTAINER_KEYCLOAK.keycloakAdminClient.also { openedClients += it }

    /** Keycloak as the backend sees it: signed in with the `aam-backend` client that was imported. */
    private fun backend(): Keycloak = KeycloakAdminConfiguration().keycloak(backendConfig).also { openedClients += it }

    @Test
    fun `should let the backend create client scopes and make them Default scopes of API clients`() {
        // Given an API client that has the scope as an Optional scope, like an integration that was set up
        // before the backend enforced the scope
        val realm = admin().realm(REALM)
        val apiClient = createClient(realm.clients(), API_CLIENT_ID)
        val initializer =
            ClientScopeInitializer(
                administration = KeycloakClientScopeAdministration(backend(), REALM, "aam-backend"),
                clientScopeRequests =
                    listOf(
                        ClientScopeRequest(
                            name = SCOPE_NAME,
                            description = "Created by the integration test of the client definitions",
                            promoteOptionalToDefault = true
                        )
                    )
            )

        try {
            // When the backend starts, which creates the scope,
            initializer.initialize()
            val scope = realm.clientScopes().findAll().single { it.name == SCOPE_NAME }
            realm.clients().get(apiClient).addOptionalClientScope(scope.id)
            // and starts again, which changes the assignment
            initializer.initialize()

            // Then
            val clientResource = realm.clients().get(apiClient)
            assertThat(clientResource.defaultClientScopes.map { it.name }).contains(SCOPE_NAME)
            assertThat(clientResource.optionalClientScopes.map { it.name }).doesNotContain(SCOPE_NAME)
        } finally {
            removeClient(realm.clients(), API_CLIENT_ID)
            val clientScopes = realm.clientScopes()
            clientScopes.findAll().filter { it.name == SCOPE_NAME }.forEach { clientScopes.get(it.id).remove() }
        }
    }

    @Test
    fun `should let the backend create a user and look it up by email and by id`() {
        // Given
        val config = backendConfig
        val backend = backend()
        val email = "definition-test-user@example.com"
        val authenticationProvider = AamKeycloakAuthenticationProvider(backend, config)

        // When
        val created =
            authenticationProvider.createExternalUser(
                firstName = "Ada",
                lastName = "Lovelace",
                email = email,
                externalUserId = "definition-test-user",
                userEntityId = null
            )

        try {
            // Then
            assertThat(authenticationProvider.findByEmail(email).orElseThrow().userId).isEqualTo(created.userId)
            assertThat(KeycloakUserEmailProvider(backend, config).lookupEmail(created.userId)).isEqualTo(email)
        } finally {
            val users = admin().realm(REALM).users()
            users.delete(created.userId).close()
        }
    }

    @Test
    fun `should let replication-backend look up a user and its realm roles with the same client`() {
        // Given the user of a request, which replication-backend resolves with these two calls
        val users = admin().realm(REALM).users()
        val userId = users.searchByUsername("app-user", true).single().id
        val user = backend().realm(REALM).users().get(userId)

        // When
        val username = user.toRepresentation().username
        val realmRoles = user.roles().realmLevel().listAll()

        // Then
        assertThat(username).isEqualTo("app-user")
        assertThat(realmRoles.map { it.name }).contains("user_app")
    }

    @Test
    fun `should issue the access tokens of the carbone render client for the oauth2 proxy`() {
        // Given the client of the oauth2 proxy, which the audience mapper refers to and which has to exist for it
        val admin = admin()
        val clients = admin.realm(REALM).clients()
        val proxyClientId = "definition-test-oauth2-proxy"
        createClient(clients, proxyClientId)

        try {
            // When the render client is imported like a deployment imports it
            KeycloakClientDefinitions.import(
                keycloak = admin,
                realm = REALM,
                definition = KeycloakClientDefinitions.CARBONE_RENDER_CLIENT,
                variables =
                    mapOf(
                        "CARBONE_REALM" to REALM,
                        "INSTANCE_NAME" to "definition-test",
                        "CARBONE_CLIENT_SECRET" to "carbone-secret",
                        "OAUTH2_PROXY_CLIENT_ID" to proxyClientId
                    )
            )
            val authTestingService = AuthTestingService(RestTemplateBuilder().rootUri(keycloakUrl).build())
            val token = authTestingService.fetchToken("carbone-definition-test", "carbone-secret", REALM)

            // Then
            assertThat(audienceOf(token)).contains(proxyClientId)
        } finally {
            removeClient(clients, "carbone-definition-test")
            removeClient(clients, proxyClientId)
        }
    }

    /** Creates a client with a service account in the realm and returns its internal id. */
    private fun createClient(
        clients: ClientsResource,
        clientId: String
    ): String {
        val representation =
            ClientRepresentation().apply {
                this.clientId = clientId
                isServiceAccountsEnabled = true
                isPublicClient = false
            }

        return clients.create(representation).use { CreatedResponseUtil.getCreatedId(it) }
    }

    private fun removeClient(
        clients: ClientsResource,
        clientId: String
    ) {
        clients.findByClientId(clientId).forEach { clients.get(it.id).remove() }
    }

    /** The `aud` claim of an access token, which Keycloak writes as a single value or as a list. */
    private fun audienceOf(accessToken: String?): List<String> {
        val payload = checkNotNull(accessToken).split(".")[1]
        val audience = jacksonObjectMapper().readTree(Base64.getUrlDecoder().decode(payload)).get("aud")

        return when {
            audience == null -> emptyList()
            audience.isArray -> audience.map { it.textValue() }
            else -> listOf(audience.textValue())
        }
    }
}
