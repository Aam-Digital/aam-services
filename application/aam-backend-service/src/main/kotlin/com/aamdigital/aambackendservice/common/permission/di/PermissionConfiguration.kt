package com.aamdigital.aambackendservice.common.permission.di

import com.aamdigital.aambackendservice.common.permission.core.PermissionCheckClient
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient

/**
 * Connection settings for the replication-backend HTTP client,
 * bound from `aam-replication-backend-client-configuration.*` properties.
 */
@ConfigurationProperties("aam-replication-backend-client-configuration")
@ConditionalOnProperty(name = ["aam-replication-backend-client-configuration.base-path"])
class ReplicationBackendClientConfiguration(
    val basePath: String,
    val basicAuthUsername: String,
    val basicAuthPassword: String,
)

/**
 * Wires the [PermissionCheckClient] with a configured replication-backend [RestClient]
 * when the `aam-replication-backend-client-configuration.base-path` property is present.
 */
@Configuration
@ConditionalOnProperty(name = ["aam-replication-backend-client-configuration.base-path"])
class PermissionConfiguration {
    @Bean(name = ["replication-backend-client"])
    fun replicationBackendClient(
        configuration: ReplicationBackendClientConfiguration
    ): RestClient {
        return RestClient
            .builder()
            .baseUrl(configuration.basePath)
            .defaultHeaders { headers ->
                headers.setBasicAuth(
                    configuration.basicAuthUsername,
                    configuration.basicAuthPassword
                )
            }
            .build()
    }

    @Bean
    fun permissionCheckClient(
        @Qualifier("replication-backend-client") replicationBackendClient: RestClient
    ): PermissionCheckClient =
        PermissionCheckClient(replicationBackendClient)
}

/**
 * Provides a no-op [PermissionCheckClient] (allow-all) when no
 * replication-backend connection is configured.
 */
@Configuration
@ConditionalOnMissingBean(PermissionCheckClient::class)
class PermissionFallbackConfiguration {
    @Bean
    fun permissionCheckClientFallback(): PermissionCheckClient =
        PermissionCheckClient()
}
