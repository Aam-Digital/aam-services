package com.aamdigital.aambackendservice.common.keycloak.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ClientScopeInitializerTest {
    /** Keycloak realm state held in memory, so the tests check the outcome instead of individual calls. */
    private class InMemoryClientScopeAdministration : ClientScopeAdministration {
        val clientScopes = mutableListOf<KeycloakClientScope>()
        val clients = mutableListOf<KeycloakServiceAccountClient>()
        var failingClientId: String? = null

        fun client(clientId: String) = clients.single { it.clientId == clientId }

        override fun findClientScopes() = clientScopes.toList()

        override fun createClientScope(
            name: String,
            description: String
        ) {
            clientScopes += KeycloakClientScope(id = "id-$name", name = name, includedInTokenScope = true)
        }

        override fun findServiceAccountClients() = clients.toList()

        override fun assignDefaultClientScope(
            client: KeycloakServiceAccountClient,
            scope: KeycloakClientScope
        ) {
            check(client.clientId != failingClientId) { "Keycloak unavailable" }
            clients.replaceAll {
                if (it.id == client.id) {
                    it.copy(
                        defaultClientScopes = it.defaultClientScopes + scope.name,
                        optionalClientScopes = it.optionalClientScopes - scope.name
                    )
                } else {
                    it
                }
            }
        }
    }

    private lateinit var administration: InMemoryClientScopeAdministration

    @BeforeEach
    fun setUp() {
        administration = InMemoryClientScopeAdministration()
    }

    private fun addClient(
        clientId: String,
        defaultScopes: Set<String> = emptySet(),
        optionalScopes: Set<String> = emptySet()
    ) {
        administration.clients +=
            KeycloakServiceAccountClient(
                id = "id-$clientId",
                clientId = clientId,
                defaultClientScopes = defaultScopes,
                optionalClientScopes = optionalScopes
            )
    }

    private fun addClientScope(name: String) {
        administration.clientScopes += KeycloakClientScope(id = "id-$name", name = name, includedInTokenScope = true)
    }

    private fun initialize(vararg requests: ClientScopeRequest) =
        ClientScopeInitializer(administration = administration, clientScopeRequests = requests.toList()).initialize()

    @Test
    fun `should create a missing client scope without assigning it to any client`() {
        // Given
        addClient("api-client")

        // When
        initialize(ClientScopeRequest(name = "reporting_read", description = "read"))

        // Then
        assertThat(administration.clientScopes.map { it.name }).containsExactly("reporting_read")
        assertThat(administration.client("api-client").defaultClientScopes).isEmpty()
    }

    @Test
    fun `should not create a client scope that already exists`() {
        // Given
        addClientScope("reporting_read")

        // When
        initialize(ClientScopeRequest(name = "reporting_read", description = "read"))

        // Then
        assertThat(administration.clientScopes).hasSize(1)
    }

    @Test
    fun `should change Optional assignments to Default if requested`() {
        // Given
        addClientScope("reporting_read")
        addClient("optional-client", optionalScopes = setOf("reporting_read", "phone"))
        addClient("unrelated-client", optionalScopes = setOf("phone"))

        // When
        initialize(ClientScopeRequest(name = "reporting_read", description = "read", promoteOptionalToDefault = true))

        // Then
        val optionalClient = administration.client("optional-client")
        assertThat(optionalClient.defaultClientScopes).containsExactly("reporting_read")
        assertThat(optionalClient.optionalClientScopes).containsExactly("phone")
        assertThat(administration.client("unrelated-client").defaultClientScopes).isEmpty()
    }

    @Test
    fun `should leave Optional assignments unchanged if not requested`() {
        // Given
        addClientScope("reporting_read")
        addClient("optional-client", optionalScopes = setOf("reporting_read"))

        // When
        initialize(ClientScopeRequest(name = "reporting_read", description = "read"))

        // Then
        assertThat(administration.client("optional-client").optionalClientScopes).containsExactly("reporting_read")
        assertThat(administration.client("optional-client").defaultClientScopes).isEmpty()
    }

    @Test
    fun `should continue with the next client scope if one fails`() {
        // Given
        addClientScope("reporting_read")
        addClient("failing-client", optionalScopes = setOf("reporting_read"))
        administration.failingClientId = "failing-client"

        // When
        initialize(
            ClientScopeRequest(name = "reporting_read", description = "read", promoteOptionalToDefault = true),
            ClientScopeRequest(name = "reporting_write", description = "write")
        )

        // Then
        assertThat(administration.clientScopes.map { it.name }).containsExactly("reporting_read", "reporting_write")
    }
}
