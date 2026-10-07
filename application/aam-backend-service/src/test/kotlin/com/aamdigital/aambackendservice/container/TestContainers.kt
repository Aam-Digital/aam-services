package com.aamdigital.aambackendservice.container

import dasniko.testcontainers.keycloak.KeycloakContainer
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName

@Testcontainers
object TestContainers {
    /** The realm of the Keycloak container, imported from `dummy-realm-realm.json`. */
    const val KEYCLOAK_REALM = "dummy-realm"

    /**
     * Secret that the `aam-backend` client is imported with, which the backend under test uses as
     * `keycloak.client-secret`.
     */
    const val AAM_BACKEND_CLIENT_SECRET = "1234"

    private var network: Network = Network.newNetwork()
    private var keycloakReady = false

    /**
     * Starts the Keycloak container and imports the `aam-backend` client into its realm from the definition that
     * ships with the application (`keycloak/aam-backend-client.json`), the way a deployment imports it.
     * The realm file leaves that client out on purpose: a role the backend needs but the definition lacks fails
     * the tests here instead of a deployment.
     *
     * Safe to call repeatedly; only the first call does anything.
     */
    @Synchronized
    fun startKeycloak() {
        if (keycloakReady) {
            return
        }

        CONTAINER_KEYCLOAK.start()
        CONTAINER_KEYCLOAK.keycloakAdminClient.use { admin ->
            KeycloakClientDefinitions.import(
                keycloak = admin,
                realm = KEYCLOAK_REALM,
                definition = KeycloakClientDefinitions.AAM_BACKEND_CLIENT,
                variables =
                    mapOf(
                        "AAM_BACKEND_REALM" to KEYCLOAK_REALM,
                        "AAM_BACKEND_CLIENT_SECRET" to AAM_BACKEND_CLIENT_SECRET
                    )
            )
        }
        keycloakReady = true
    }

    @DynamicPropertySource
    @JvmStatic
    fun init(registry: DynamicPropertyRegistry) {
        startKeycloak()
        CONTAINER_COUCHDB.start()
        CONTAINER_SQS.start()
        CONTAINER_PDF.start()
        registry.add(
            "spring.security.oauth2.resourceserver.jwt.issuer-uri"
        ) {
            "http://localhost:${CONTAINER_KEYCLOAK.getMappedPort(8080)}/realms/dummy-realm"
        }
        registry.add(
            "aam-security.allowed-issuers"
        ) {
            "http://localhost:${CONTAINER_KEYCLOAK.getMappedPort(8080)}"
        }
        registry.add(
            "keycloak.server-url"
        ) {
            "http://localhost:${CONTAINER_KEYCLOAK.getMappedPort(8080)}"
        }
        registry.add(
            "keycloak.client-secret"
        ) {
            AAM_BACKEND_CLIENT_SECRET
        }
        registry.add(
            "couch-db-client-configuration.base-path"
        ) {
            "http://localhost:${CONTAINER_COUCHDB.getMappedPort(5984)}"
        }
        registry.add(
            "sqs-client-configuration.base-path"
        ) {
            "http://localhost:${CONTAINER_SQS.getMappedPort(4984)}"
        }
        registry.add(
            "aam-render-api-client-configuration.base-path"
        ) {
            "http://localhost:${CONTAINER_PDF.getMappedPort(4000)}"
        }
        registry.add(
            "aam-render-api-client-configuration.auth-config.token-endpoint"
        ) {
            "http://localhost:${CONTAINER_KEYCLOAK.getMappedPort(8080)}" +
                "/realms/dummy-realm/protocol/openid-connect/token"
        }
    }

    @Container
    @JvmStatic
    val CONTAINER_KEYCLOAK: KeycloakContainer =
        KeycloakContainer()
//        .withEnv("JAVA_TOOL_OPTIONS", "-XX:UseSVE=0") # bug on M4 chips with Sequoia 15.2: https://github.com/corretto/corretto-21/issues/85
            .withRealmImportFile("/dummy-realm-realm.json")
            .withAdminUsername("admin")
            .withAdminPassword("docker")

    @Container
    @JvmStatic
    val CONTAINER_COUCHDB: GenericContainer<*> =
        GenericContainer(
            DockerImageName
                .parse("couchdb")
                .withTag(TestImages.COUCHDB)
        ).withNetwork(network)
            .withNetworkAliases("couchdb")
            .withEnv(
                mapOf(
                    Pair("COUCHDB_USER", "admin"),
                    Pair("COUCHDB_PASSWORD", "docker"),
                    Pair("COUCHDB_SECRET", "docker")
                )
            ).withExposedPorts(5984)

    @Container
    @JvmStatic
    val CONTAINER_SQS: GenericContainer<*> =
        GenericContainer(
            DockerImageName
                .parse("ghcr.io/aam-digital/sqs-aam")
                .asCompatibleSubstituteFor("sqs-aam")
                .withTag(TestImages.SQS)
        ).withNetwork(network)
            .withNetworkAliases("sqs")
            .withEnv(
                mapOf(
                    Pair("SQS_COUCHDB_URL", "http://couchdb:5984")
                )
            ).withExposedPorts(4984)

    @Container
    @JvmStatic
    val CONTAINER_PDF: GenericContainer<*> =
        GenericContainer(
            DockerImageName
                .parse("carbone/carbone-ee")
                .withTag(TestImages.CARBONE)
        ).withNetwork(network)
            .withNetworkAliases("pdf")
            .withExposedPorts(4000)
}
