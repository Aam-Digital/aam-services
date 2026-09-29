package com.aamdigital.aambackendservice.common.keycloak.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ClientScopeInitializerTest {
    /** Keycloak realm state held in memory, so the tests check the outcome instead of individual calls. */
    private class InMemoryClientScopeAdministration : ClientScopeAdministration {
        val clientScopes = mutableListOf<KeycloakClientScope>()
        val clients = mutableListOf<KeycloakServiceAccountClient>()
        val realmRoles = mutableSetOf<String>()
        val serviceAccountRoles = mutableMapOf<String, Set<String>>()
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

        override fun realmRoleExists(roleName: String) = roleName in realmRoles

        override fun serviceAccountHasRealmRole(
            client: KeycloakServiceAccountClient,
            roleName: String
        ) = roleName in serviceAccountRoles[client.clientId].orEmpty()

        override fun deleteRealmRole(roleName: String) {
            realmRoles -= roleName
            serviceAccountRoles.replaceAll { _, roles -> roles - roleName }
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
        optionalScopes: Set<String> = emptySet(),
        realmRoles: Set<String> = emptySet()
    ) {
        administration.clients +=
            KeycloakServiceAccountClient(
                id = "id-$clientId",
                clientId = clientId,
                defaultClientScopes = defaultScopes,
                optionalClientScopes = optionalScopes
            )
        administration.serviceAccountRoles[clientId] = realmRoles
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
    fun `should assign the scope to clients holding the replaced realm role and delete the role`() {
        // Given
        administration.realmRoles += "legacy-role"
        addClient("legacy-client", realmRoles = setOf("legacy-role"))
        addClient("other-client", realmRoles = setOf("user_app"))

        // When
        initialize(ClientScopeRequest(name = "new_scope", description = "new", replacesRealmRole = "legacy-role"))

        // Then
        assertThat(administration.client("legacy-client").defaultClientScopes).containsExactly("new_scope")
        assertThat(administration.client("other-client").defaultClientScopes).isEmpty()
        assertThat(administration.realmRoles).doesNotContain("legacy-role")
    }

    @Test
    fun `should skip the role migration if the replaced realm role does not exist`() {
        // Given
        addClient("api-client")

        // When
        initialize(ClientScopeRequest(name = "new_scope", description = "new", replacesRealmRole = "legacy-role"))

        // Then
        assertThat(administration.clientScopes.map { it.name }).containsExactly("new_scope")
        assertThat(administration.client("api-client").defaultClientScopes).isEmpty()
    }

    @Test
    fun `should keep the replaced realm role if a client could not be migrated`() {
        // Given
        administration.realmRoles += "legacy-role"
        addClient("legacy-client", realmRoles = setOf("legacy-role"))
        administration.failingClientId = "legacy-client"

        // When
        initialize(ClientScopeRequest(name = "new_scope", description = "new", replacesRealmRole = "legacy-role"))

        // Then
        assertThat(administration.realmRoles).contains("legacy-role")
        assertThat(administration.serviceAccountRoles["legacy-client"]).contains("legacy-role")
    }

    @Test
    fun `should still migrate the other clients if one client fails`() {
        // Given
        administration.realmRoles += "legacy-role"
        addClient("failing-client", realmRoles = setOf("legacy-role"))
        addClient("legacy-client", realmRoles = setOf("legacy-role"))
        administration.failingClientId = "failing-client"

        // When
        initialize(ClientScopeRequest(name = "new_scope", description = "new", replacesRealmRole = "legacy-role"))

        // Then
        assertThat(administration.client("legacy-client").defaultClientScopes).containsExactly("new_scope")
        assertThat(administration.client("failing-client").defaultClientScopes).isEmpty()
        assertThat(administration.realmRoles).contains("legacy-role")
    }

    @Test
    fun `should keep the replaced realm role if the client scope is not included in tokens`() {
        // Given
        administration.clientScopes +=
            KeycloakClientScope(id = "id-new_scope", name = "new_scope", includedInTokenScope = false)
        administration.realmRoles += "legacy-role"
        addClient("legacy-client", realmRoles = setOf("legacy-role"))

        // When
        initialize(ClientScopeRequest(name = "new_scope", description = "new", replacesRealmRole = "legacy-role"))

        // Then
        assertThat(administration.realmRoles).contains("legacy-role")
        assertThat(administration.client("legacy-client").defaultClientScopes).isEmpty()
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
