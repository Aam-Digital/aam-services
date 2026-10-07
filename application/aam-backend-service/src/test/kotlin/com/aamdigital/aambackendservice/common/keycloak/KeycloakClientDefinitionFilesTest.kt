package com.aamdigital.aambackendservice.common.keycloak

import com.aamdigital.aambackendservice.container.KeycloakClientDefinitions
import com.aamdigital.aambackendservice.container.KeycloakClientDefinitions.AAM_BACKEND_CLIENT
import com.aamdigital.aambackendservice.container.KeycloakClientDefinitions.CARBONE_RENDER_CLIENT
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Pins what deployments rely on in the Keycloak client definitions that ship with the application (see
 * `keycloak/README.md`): the file names in the image, the variables, and that the files never contain a secret.
 * Changing one of them is a breaking change for the deployments that import the files, so it has to show up in
 * a diff of this test and in the release notes.
 *
 * That the definitions work in Keycloak is proven by [KeycloakClientDefinitionsIntegrationTest].
 */
class KeycloakClientDefinitionFilesTest {
    private val placeholder = Regex("""^\$\(env:\w+\)$""")

    @Test
    fun `should ship the definitions under the file names deployments read`() {
        // When
        val names = KeycloakClientDefinitions.ALL.map { it.name }

        // Then
        assertThat(names).containsExactlyInAnyOrder("aam-backend-client.json", "carbone-render-client.json")
        assertThat(KeycloakClientDefinitions.ALL).allSatisfy { assertThat(it).isFile() }
    }

    @Test
    fun `should copy every definition into the same folder of the image, where deployments read them`() {
        // Given
        val dockerfile = File("Dockerfile").readText()

        // Then
        assertThat(KeycloakClientDefinitions.ALL).allSatisfy { definition ->
            val folder = Regex.escape(definition.parentFile.invariantSeparatorsPath)
            assertThat(dockerfile).containsPattern("""(?m)^COPY\s+$folder/\s+/opt/app/keycloak/\s*$""")
        }
    }

    @Test
    fun `should use the variables that deployments fill in`() {
        // When
        val variables = KeycloakClientDefinitions.ALL.flatMap { KeycloakClientDefinitions.variablesOf(it) }

        // Then
        assertThat(variables).containsExactlyInAnyOrder(
            "AAM_BACKEND_REALM",
            "AAM_BACKEND_CLIENT_SECRET",
            "CARBONE_REALM",
            "INSTANCE_NAME",
            "CARBONE_CLIENT_SECRET",
            "OAUTH2_PROXY_CLIENT_ID"
        )
    }

    @Test
    fun `should document the variables of each definition where deployments look for them`() {
        // Given the README of the definitions for aam-backend, and the documentation of the export module for Carbone
        val aamBackendReadme = File(AAM_BACKEND_CLIENT.parentFile, "README.md").readText()
        val exportDocumentation = File("../../docs/modules/export.md").readText()
        val carboneSection = exportDocumentation.substringAfter("### OAuth Proxy & Keycloak Client", "")

        // Then
        assertThat(documentedVariables(aamBackendReadme))
            .isEqualTo(KeycloakClientDefinitions.variablesOf(AAM_BACKEND_CLIENT))
        assertThat(documentedVariables(carboneSection))
            .isEqualTo(KeycloakClientDefinitions.variablesOf(CARBONE_RENDER_CLIENT))
    }

    @Test
    fun `should hold no secret but a placeholder for it`() {
        // When
        val secrets =
            KeycloakClientDefinitions.ALL
                .flatMap { definition -> KeycloakClientDefinitions.read(definition).clients.map { it.secret } }

        // Then
        assertThat(secrets).hasSize(2).allSatisfy { assertThat(it).matches(placeholder.toPattern()) }
    }

    @Test
    fun `should name the realm by a variable, as keycloak-config-cli cannot import a file without one`() {
        // When
        val realms = KeycloakClientDefinitions.ALL.map { KeycloakClientDefinitions.read(it).realm }

        // Then
        assertThat(realms).hasSize(2).allSatisfy { assertThat(it).matches(placeholder.toPattern()) }
    }

    @Test
    fun `should define aam-backend as a confidential service account client without interactive login`() {
        // When
        val client = KeycloakClientDefinitions.read(AAM_BACKEND_CLIENT).clients.single()

        // Then
        assertThat(client.clientId).isEqualTo("aam-backend")
        assertThat(client.isPublicClient).isFalse()
        assertThat(client.isServiceAccountsEnabled).isTrue()
        assertThat(client.isStandardFlowEnabled).isFalse()
        assertThat(client.isImplicitFlowEnabled).isFalse()
        assertThat(client.isDirectAccessGrantsEnabled).isFalse()
        assertThat(client.defaultClientScopes).containsExactly("roles")
    }

    @Test
    fun `should grant the aam-backend service account only the roles that the services need`() {
        // When
        val serviceAccount = KeycloakClientDefinitions.read(AAM_BACKEND_CLIENT).users.single()

        // Then
        assertThat(serviceAccount.clientRoles).containsOnlyKeys("realm-management")
        assertThat(serviceAccount.clientRoles["realm-management"])
            .describedAs("every deployment gets a role that is added here: update the README and the release notes")
            .containsExactlyInAnyOrder("manage-clients", "manage-users", "view-users")
    }

    @Test
    fun `should define the carbone render client as a confidential service account client`() {
        // When
        val client = KeycloakClientDefinitions.read(CARBONE_RENDER_CLIENT).clients.single()

        // Then
        assertThat(client.clientId).isEqualTo("carbone-\$(env:INSTANCE_NAME)")
        assertThat(client.isPublicClient).isFalse()
        assertThat(client.isServiceAccountsEnabled).isTrue()
        assertThat(client.isStandardFlowEnabled).isFalse()
        assertThat(client.isImplicitFlowEnabled).isFalse()
        assertThat(client.isDirectAccessGrantsEnabled).isFalse()
    }

    @Test
    fun `should add the oauth2 proxy to the audience of the access tokens of the carbone render client`() {
        // When
        val audienceMapper =
            KeycloakClientDefinitions
                .read(CARBONE_RENDER_CLIENT)
                .clients
                .single()
                .protocolMappers
                .single()

        // Then
        assertThat(audienceMapper.protocolMapper).isEqualTo("oidc-audience-mapper")
        assertThat(audienceMapper.config)
            .containsEntry("included.client.audience", "\$(env:OAUTH2_PROXY_CLIENT_ID)")
            .containsEntry("access.token.claim", "true")
            .containsEntry("id.token.claim", "false")
    }

    @Test
    fun `should define the service account user of each client, which a partial import does not create`() {
        KeycloakClientDefinitions.ALL.forEach { definitionFile ->
            // When
            val definition = KeycloakClientDefinitions.read(definitionFile)
            val client = definition.clients.single()
            val serviceAccount = definition.users.single()

            // Then
            assertThat(serviceAccount.serviceAccountClientId).isEqualTo(client.clientId)
            assertThat(serviceAccount.username).isEqualTo("service-account-${client.clientId}")
        }
    }

    /** The variables in the first column of the tables of [markdown], like `| `NAME` | value |`. */
    private fun documentedVariables(markdown: String): Set<String> =
        Regex("""(?m)^\|\s*`([A-Z][A-Z0-9_]*)`\s*\|""")
            .findAll(markdown)
            .map { it.groupValues[1] }
            .toSet()
}
