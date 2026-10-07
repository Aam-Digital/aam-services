package com.aamdigital.aambackendservice.container

import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.startupcheck.OneShotStartupCheckStrategy
import org.testcontainers.utility.DockerImageName
import org.testcontainers.utility.MountableFile
import java.io.File
import java.time.Duration

/**
 * Imports one of the client definitions that ship with the application (`keycloak/`) into the Keycloak container
 * with keycloak-config-cli, with the settings that the README of the definitions recommends.
 */
object KeycloakConfigCli {
    private const val KEYCLOAK_HTTP_PORT = 8080
    private val IMPORT_TIMEOUT = Duration.ofMinutes(2)

    /** The settings of the README: variables are filled in, and the import neither deletes nor remembers anything. */
    private val RECOMMENDED_SETTINGS =
        mapOf(
            "IMPORT_VARSUBSTITUTION_ENABLED" to "true",
            "IMPORT_MANAGED_CLIENT" to "no-delete",
            "IMPORT_REMOTE_STATE_ENABLED" to "false",
            "IMPORT_CACHE_ENABLED" to "false"
        )

    /**
     * Runs keycloak-config-cli for [fileName] with [variables] as its environment, in a container that exits when
     * the import is done. A failed import throws, with the output of the tool.
     */
    fun import(
        fileName: String,
        variables: Map<String, String>
    ) {
        val keycloakPort = TestContainers.CONTAINER_KEYCLOAK.getMappedPort(KEYCLOAK_HTTP_PORT)
        val container =
            GenericContainer(
                DockerImageName.parse("adorsys/keycloak-config-cli").withTag(TestImages.KEYCLOAK_CONFIG_CLI)
            ).withExtraHost("host.docker.internal", "host-gateway")
                .withCopyFileToContainer(
                    MountableFile.forHostPath(File(KeycloakClientDefinitions.DIRECTORY, fileName).absolutePath),
                    "/definitions/$fileName"
                ).withEnv(
                    mapOf(
                        "KEYCLOAK_URL" to "http://host.docker.internal:$keycloakPort",
                        "KEYCLOAK_USER" to "admin",
                        "KEYCLOAK_PASSWORD" to "docker",
                        "KEYCLOAK_AVAILABILITYCHECK_ENABLED" to "false",
                        "IMPORT_FILES_LOCATIONS" to "/definitions/$fileName"
                    ) + RECOMMENDED_SETTINGS + variables
                ).withStartupCheckStrategy(OneShotStartupCheckStrategy().withTimeout(IMPORT_TIMEOUT))

        container.use {
            try {
                it.start()
            } catch (e: Exception) {
                throw IllegalStateException("Importing $fileName with keycloak-config-cli failed:\n${it.logs}", e)
            }
        }
    }
}
