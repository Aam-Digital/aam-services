package com.aamdigital.aambackendservice.container

import org.keycloak.admin.client.Keycloak
import org.keycloak.representations.idm.PartialImportRepresentation
import org.keycloak.representations.idm.RealmRepresentation
import org.keycloak.util.JsonSerialization
import java.io.File

/**
 * The Keycloak client definitions that ship with the application (`keycloak/`, see its README), loaded the way
 * a deployment loads them: the placeholders are filled in, then the clients and their service account users
 * are imported into a realm.
 */
object KeycloakClientDefinitions {
    const val AAM_BACKEND_CLIENT = "aam-backend-client.json"
    const val CARBONE_RENDER_CLIENT = "carbone-render-client.json"

    /** Where the definitions live; the tests run in the module directory. */
    val DIRECTORY = File("keycloak")

    private const val HTTP_OK = 200
    private val PLACEHOLDER = Regex("""\$\(env:(\w+)\)""")

    /** Names of all variables that the definition [fileName] contains placeholders for. */
    fun variablesOf(fileName: String): Set<String> =
        PLACEHOLDER
            .findAll(File(DIRECTORY, fileName).readText())
            .map { it.groupValues[1] }
            .toSet()

    /** The definition [fileName] as Keycloak reads it, with its placeholders as they are. */
    fun read(fileName: String): RealmRepresentation =
        JsonSerialization.readValue(File(DIRECTORY, fileName).readText(), RealmRepresentation::class.java)

    /**
     * The content of [fileName] with every `$(env:NAME)` placeholder replaced.
     * Like keycloak-config-cli, a variable that is not given is an error rather than an empty value.
     */
    fun load(
        fileName: String,
        variables: Map<String, String>
    ): String =
        PLACEHOLDER.replace(File(DIRECTORY, fileName).readText()) { placeholder ->
            val name = placeholder.groupValues[1]
            variables[name] ?: error("Variable $name of $fileName is not set")
        }

    /**
     * Imports the clients and service account users of [fileName] into [realm] with Keycloak's partial import.
     * What exists in the realm already is left as it is, so importing twice is harmless.
     */
    fun import(
        keycloak: Keycloak,
        realm: String,
        fileName: String,
        variables: Map<String, String>
    ) {
        val definition = JsonSerialization.readValue(load(fileName, variables), RealmRepresentation::class.java)
        val partialImport =
            PartialImportRepresentation().apply {
                ifResourceExists = "SKIP"
                clients = definition.clients
                users = definition.users
            }

        keycloak.realm(realm).partialImport(partialImport).use { response ->
            check(response.status == HTTP_OK) {
                "Importing $fileName into realm $realm failed with HTTP ${response.status}: " +
                    response.readEntity(String::class.java)
            }
        }
    }
}
