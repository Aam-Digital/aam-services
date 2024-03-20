package com.aamdigital.aambackendservice.container

import dasniko.testcontainers.keycloak.KeycloakContainer
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.RabbitMQContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName

@Testcontainers
object TestContainers {

    @DynamicPropertySource
    @JvmStatic
    fun init(registry: DynamicPropertyRegistry) {
        CONTAINER_KEYCLOAK.start()
        CONTAINER_COUCHDB.start()
        CONTAINER_SQS.start()
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
    val CONTAINER_SQS: GenericContainer<*> =
        GenericContainer(
            DockerImageName
                .parse("ghcr.io/aam-digital/aam-sqs-mac")
                .withTag("latest")
        )
            .withEnv(
                mapOf(
                    Pair("SQS_COUCHDB_URL", "http://db-couch:5984"),
                )
            )
            .withExposedPorts(4984)
}
