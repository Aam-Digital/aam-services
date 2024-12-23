package com.aamdigital.aambackendservice.container

import dasniko.testcontainers.keycloak.KeycloakContainer
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.RabbitMQContainer
import org.testcontainers.images.PullPolicy
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName

@Testcontainers
object TestContainers {

    private var network: Network = Network.newNetwork()

    @DynamicPropertySource
    @JvmStatic
    fun init(registry: DynamicPropertyRegistry) {
        CONTAINER_KEYCLOAK.start()
        CONTAINER_COUCHDB.start()
        CONTAINER_SQS.start()
        CONTAINER_PDF.start()
        CONTAINER_POSTGRES.start()
        registry.add(
            "spring.security.oauth2.resourceserver.jwt.issuer-uri"
        ) {
            "http://localhost:${CONTAINER_KEYCLOAK.getMappedPort(8080)}/realms/dummy-realm"
        }
        registry.add(
            "couch-db-client-configuration.base-path",
        ) {
            "http://localhost:${CONTAINER_COUCHDB.getMappedPort(5984)}"
        }
        registry.add(
            "sqs-client-configuration.base-path",
        ) {
            "http://localhost:${CONTAINER_SQS.getMappedPort(4984)}"
        }
        registry.add(
            "aam-render-api-client-configuration.base-path",
        ) {
            "http://localhost:${CONTAINER_PDF.getMappedPort(4000)}"
        }
        registry.add(
            "aam-render-api-client-configuration.auth-config.token-endpoint",
        ) {
            "http://localhost:${CONTAINER_KEYCLOAK.getMappedPort(8080)}" +
                    "/realms/dummy-realm/protocol/openid-connect/token"
        }
        registry.add(
            "spring.datasource.url",
        ) {
            "jdbc:postgresql://localhost:${CONTAINER_POSTGRES.getMappedPort(5432)}" +
                    "/aam_backend_service_test"
        }
    }

    @Container
    @JvmStatic
    val CONTAINER_KEYCLOAK: KeycloakContainer = KeycloakContainer()
        .withRealmImportFile("/e2e-keycloak-realm.json")
        .withAdminUsername("admin")
        .withAdminPassword("docker")

    @Container
    @ServiceConnection
    @JvmStatic
    val CONTAINER_RABBIT_MQ: RabbitMQContainer = RabbitMQContainer(
        DockerImageName
            .parse("rabbitmq")
            .withTag("3.7.25-management-alpine")
    )

    @Container
    @JvmStatic
    val CONTAINER_COUCHDB: GenericContainer<*> =
        GenericContainer(
            DockerImageName
                .parse("couchdb")
                .withTag("3.3")
        )
            .withNetwork(network)
            .withNetworkAliases("couchdb")
            .withEnv(
                mapOf(
                    Pair("COUCHDB_USER", "admin"),
                    Pair("COUCHDB_PASSWORD", "docker"),
                    Pair("COUCHDB_SECRET", "docker"),
                )
            )
            .withExposedPorts(5984)

    @Container
    @JvmStatic
    val CONTAINER_POSTGRES: GenericContainer<*> =
        GenericContainer(
            DockerImageName
                .parse("postgres")
                .withTag("16.5-bookworm")
        )
            .withNetwork(network)
            .withNetworkAliases("postgres")
            .withEnv(
                mapOf(
                    Pair("POSTGRES_DB", "aam_backend_service_test"),
                    Pair("POSTGRES_USER", "admin"),
                    Pair("POSTGRES_PASSWORD", "docker"),
                )
            )
            .withExposedPorts(5432)

    @Container
    @JvmStatic
    val CONTAINER_SQS: GenericContainer<*> =
        GenericContainer(
            DockerImageName
                .parse("ghcr.io/aam-digital/aam-sqs-linux")
                .asCompatibleSubstituteFor("aam-sqs-linux")
                .withTag("latest")
        )
            .withImagePullPolicy(PullPolicy.alwaysPull())
            .withNetwork(network)
            .withNetworkAliases("sqs")
            .withEnv(
                mapOf(
                    Pair("SQS_COUCHDB_URL", "http://couchdb:5984"),
                )
            )
            .withExposedPorts(4984)

    @Container
    @JvmStatic
    val CONTAINER_PDF: GenericContainer<*> =
        GenericContainer(
            DockerImageName
                .parse("carbone/carbone-ee")
                .withTag("4.23.4")
        )
            .withImagePullPolicy(PullPolicy.alwaysPull())
            .withNetwork(network)
            .withNetworkAliases("pdf")
            .withExposedPorts(4000)

}
