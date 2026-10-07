package com.aamdigital.aambackendservice.container

import org.keycloak.admin.client.Keycloak
import org.keycloak.representations.idm.PartialImportRepresentation
import org.keycloak.representations.idm.RealmRepresentation
import org.keycloak.util.JsonSerialization
import java.io.File

/**
 * The Keycloak client definitions that ship with the application (see `keycloak/README.md`), loaded the way
 * a deployment loads them: the placeholders are filled in, then the clients and their service account users
 * are imported into a realm.
 *
 * The tests run in the module directory, so the paths are relative to it.
 */
object KeycloakClientDefinitions {
    /** Used by several modules and by replication-backend, so it lives in the `keycloak` folder of the module. */
    val AAM_BACKEND_CLIENT = File("keycloak/aam-backend-client.json")

    /** Only the export module needs it, so it lives with that module. */
    val CARBONE_RENDER_CLIENT =
        File("src/main/kotlin/com/aamdigital/aambackendservice/export/keycloak/carbone-render-client.json")

    /** All definitions. The Dockerfile copies the folder of each into the same folder of the image. */
    val ALL = listOf(AAM_BACKEND_CLIENT, CARBONE_RENDER_CLIENT)

    private const val HTTP_OK = 200
    private val PLACEHOLDER = Regex("""\$\(env:(\w+)\)""")

    /** Names of all variables that [definition] contains placeholders for. */
    fun variablesOf(definition: File): Set<String> =
        PLACEHOLDER
            .findAll(definition.readText())
            .map { it.groupValues[1] }
            .toSet()

    /** [definition] as Keycloak reads it, with its placeholders as they are. */
    fun read(definition: File): RealmRepresentation =
        JsonSerialization.readValue(definition.readText(), RealmRepresentation::class.java)

    /**
     * The content of [definition] with every `$(env:NAME)` placeholder replaced.
     * Like keycloak-config-cli, a variable that is not given is an error rather than an empty value.
     */
    fun load(
        definition: File,
        variables: Map<String, String>
    ): String =
        PLACEHOLDER.replace(definition.readText()) { placeholder ->
            val name = placeholder.groupValues[1]
            variables[name] ?: error("Variable $name of ${definition.name} is not set")
        }

    /**
     * Imports the clients and service account users of [definition] into [realm] with Keycloak's partial import.
     * What exists in the realm already is left as it is, so importing twice is harmless.
     */
    fun import(
        keycloak: Keycloak,
        realm: String,
        definition: File,
        variables: Map<String, String>
    ) {
        val representation = JsonSerialization.readValue(load(definition, variables), RealmRepresentation::class.java)
        val partialImport =
            PartialImportRepresentation().apply {
                ifResourceExists = "SKIP"
                clients = representation.clients
                users = representation.users
            }

        keycloak.realm(realm).partialImport(partialImport).use { response ->
            check(response.status == HTTP_OK) {
                "Importing ${definition.name} into realm $realm failed with HTTP ${response.status}: " +
                    response.readEntity(String::class.java)
            }
        }
    }
}
