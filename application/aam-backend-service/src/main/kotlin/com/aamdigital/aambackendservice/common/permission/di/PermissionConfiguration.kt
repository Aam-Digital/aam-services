package com.aamdigital.aambackendservice.common.permission.di

import com.aamdigital.aambackendservice.common.permission.core.PermissionCheckClient
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient

/**
 * Connection settings for the replication-backend HTTP client,
 * bound from `aam-replication-backend-client-configuration.*` properties.
 *
 * [basePath] defaults to empty; set it to a non-blank URL to enable
 * permission-aware notification filtering. Set it to an empty string
 * (or remove the env var) to explicitly disable permission checks (allow-all).
 */
@ConfigurationProperties("aam-replication-backend-client-configuration")
class ReplicationBackendClientConfiguration(
    val basePath: String = "",
    val basicAuthUsername: String = "",
    val basicAuthPassword: String = "",
)

/**
 * Wires the [PermissionCheckClient] bean.
 *
 * When `aam-replication-backend-client-configuration.base-path` is set to a non-blank value,
 * a [RestClient] is created and permission checks are performed against the replication-backend.
 *
 * When the property is blank or absent, a no-op allow-all [PermissionCheckClient] is provided instead.
 * This is the expected setup when no access control / Config:Permissions is configured
 * (i.e. all authenticated users have "manage all" permissions).
 */
@Configuration
class PermissionConfiguration {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Bean
    fun permissionCheckClient(
        configuration: ReplicationBackendClientConfiguration
    ): PermissionCheckClient {
        if (configuration.basePath.isBlank()) {
            logger.info(
                "aam-replication-backend-client-configuration.base-path is not configured. " +
                    "Permission checks are disabled; all notifications will be allowed."
            )
            return PermissionCheckClient()
        }

        logger.info(
            "Permission-aware notification filtering enabled (replication-backend: {})",
            configuration.basePath
        )

        val restClient = RestClient.builder()
            .baseUrl(configuration.basePath)
            .defaultHeaders { headers ->
                headers.setBasicAuth(
                    configuration.basicAuthUsername,
                    configuration.basicAuthPassword
                )
            }
            .build()

        return PermissionCheckClient(restClient)
    }
}
