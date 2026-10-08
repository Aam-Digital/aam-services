package com.aamdigital.aambackendservice.common.keycloak

import com.aamdigital.aambackendservice.common.AuthTestingService
import com.aamdigital.aambackendservice.container.KeycloakClientDefinitions
import com.aamdigital.aambackendservice.container.KeycloakConfigCli
import com.aamdigital.aambackendservice.container.TestContainers
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.keycloak.admin.client.Keycloak
import org.keycloak.admin.client.resource.RealmResource
import org.keycloak.representations.idm.ClientRepresentation
import org.keycloak.representations.idm.RealmRepresentation
import org.springframework.boot.restclient.RestTemplateBuilder
import java.util.Base64

/**
 * Proves what the README of the client definitions promises for an import with keycloak-config-cli, which is how
 * deployments import them: it changes a client that exists already, and it leaves the clients of other instances
 * in the shared realm alone. (Keycloak's partial import, which the other tests use, leaves existing clients as they are.)
 */
class KeycloakConfigCliImportTest {
    companion object {
        private const val REMOTE_STATE_PREFIX = "de.adorsys.keycloak.config.state-"

        @BeforeAll
        @JvmStatic
        fun startKeycloak() {
            TestContainers.startKeycloak()
        }
    }

    private val openedClients = mutableListOf<Keycloak>()
    private val keycloakUrl get() = TestContainers.CONTAINER_KEYCLOAK.authServerUrl.trimEnd('/')
    private val createdRealms = mutableListOf<String>()

    @AfterEach
    fun removeRealmsAndCloseClients() {
        createdRealms.forEach { admin().realm(it).remove() }
        openedClients.forEach { it.close() }
    }

    private fun admin(): Keycloak = TestContainers.CONTAINER_KEYCLOAK.keycloakAdminClient.also { openedClients += it }

    private fun createRealm(name: String): RealmResource {
        val admin = admin()
        admin.realms().create(
            RealmRepresentation().apply {
                realm = name
                isEnabled = true
            }
        )
        createdRealms += name
        return admin.realm(name)
    }

    private fun defaultScopesOf(
        realm: RealmResource,
        clientId: String
    ): List<String> =
        realm
            .clients()
            .get(clientId)
            .defaultClientScopes
            .map { it.name }

    private fun realmManagementRolesOf(
        realm: RealmResource,
        userId: String
    ): List<String> {
        val realmManagementId =
            realm
                .clients()
                .findByClientId("realm-management")
                .single()
                .id
        return realm
            .users()
            .get(userId)
            .roles()
            .clientLevel(realmManagementId)
            .listAll()
            .map { it.name }
    }

    @Test
    fun `should bring an aam-backend client of an earlier deployment in line with the definition`() {
        // Given a client the way the deployment scripts used to create it: five roles, and the realm's default scopes
        val realm = createRealm("config-cli-existing")
        val backendId =
            realm
                .clients()
                .create(
                    ClientRepresentation().apply {
                        clientId = "aam-backend"
                        secret = "old-secret"
                        isPublicClient = false
                        isServiceAccountsEnabled = true
                    }
                ).use { response -> response.location.path.substringAfterLast('/') }
        val serviceAccountId =
            realm
                .clients()
                .get(backendId)
                .serviceAccountUser.id
        val realmManagementId =
            realm
                .clients()
                .findByClientId("realm-management")
                .single()
                .id
        val oldRoles = listOf("manage-clients", "manage-users", "view-users", "query-users", "manage-realm")
        realm
            .users()
            .get(serviceAccountId)
            .roles()
            .clientLevel(realmManagementId)
            .add(
                realm
                    .clients()
                    .get(realmManagementId)
                    .roles()
                    .list()
                    .filter { it.name in oldRoles }
            )
        assertThat(realmManagementRolesOf(realm, serviceAccountId)).hasSize(oldRoles.size)
        assertThat(defaultScopesOf(realm, backendId)).hasSizeGreaterThan(1)

        // When the definition is imported with the secret the deployment passes in
        KeycloakConfigCli.import(
            fileName = KeycloakClientDefinitions.AAM_BACKEND_CLIENT,
            variables = mapOf("AAM_BACKEND_REALM" to "config-cli-existing", "AAM_BACKEND_CLIENT_SECRET" to "new-secret")
        )

        // Then it is the same client, which now matches the definition
        assertThat(
            realm
                .clients()
                .findByClientId("aam-backend")
                .single()
                .id
        ).isEqualTo(backendId)
        assertThat(realmManagementRolesOf(realm, serviceAccountId))
            .containsExactlyInAnyOrder("manage-clients", "manage-users", "view-users")
        assertThat(defaultScopesOf(realm, backendId))
            .describedAs("the scopes of the realm are gone, only the scope that Keycloak gives service accounts stays")
            .contains("roles")
            .doesNotContain("basic", "profile", "email", "web-origins", "acr")
        assertThat(
            realm
                .clients()
                .get(backendId)
                .secret.value
        ).isEqualTo("new-secret")

        // and the token of the client has a subject, although the scope that usually adds it is gone
        val token =
            AuthTestingService(RestTemplateBuilder().rootUri(keycloakUrl).build())
                .fetchToken("aam-backend", "new-secret", "config-cli-existing")
        val claims = jacksonObjectMapper().readTree(Base64.getUrlDecoder().decode(checkNotNull(token).split(".")[1]))
        assertThat(claims.get("sub").textValue()).isEqualTo(serviceAccountId)
    }

    @Test
    fun `should leave the render clients of other instances in the shared realm alone`() {
        // Given the shared realm, which every instance imports its render client into
        val realm = createRealm("config-cli-shared")

        // When two instances import theirs, one after the other
        listOf("first", "second").forEach { instance ->
            KeycloakConfigCli.import(
                fileName = KeycloakClientDefinitions.CARBONE_RENDER_CLIENT,
                variables =
                    mapOf(
                        "CARBONE_REALM" to "config-cli-shared",
                        "INSTANCE_NAME" to instance,
                        "CARBONE_CLIENT_SECRET" to "secret-of-$instance",
                        "OAUTH2_PROXY_CLIENT_ID" to "oauth2-proxy"
                    )
            )
        }

        // Then both clients exist, and the import left no state in the realm for a later import to delete them by
        assertThat(realm.clients().findAll().map { it.clientId })
            .contains("carbone-first", "carbone-second")
        assertThat(realm.toRepresentation().attributes.keys).noneMatch { it.startsWith(REMOTE_STATE_PREFIX) }

        // and the first client still has the secret and the single audience mapper of its own import
        val firstId =
            realm
                .clients()
                .findByClientId("carbone-first")
                .single()
                .id
        val first = realm.clients().get(firstId)
        assertThat(first.secret.value).isEqualTo("secret-of-first")
        assertThat(
            first.protocolMappers.mappers
                .filter { it.protocolMapper == "oidc-audience-mapper" }
                .map { it.config["included.client.audience"] }
        ).containsExactly("oauth2-proxy")
    }
}
