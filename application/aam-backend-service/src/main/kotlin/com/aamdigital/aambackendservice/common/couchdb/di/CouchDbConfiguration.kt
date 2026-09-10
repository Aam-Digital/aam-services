package com.aamdigital.aambackendservice.common.couchdb.di

import com.aamdigital.aambackendservice.common.couchdb.core.CouchDbClient
import com.aamdigital.aambackendservice.common.couchdb.core.CouchDbFileStorage
import com.aamdigital.aambackendservice.common.couchdb.core.CouchDbInitializer
import com.aamdigital.aambackendservice.common.couchdb.core.DatabaseRequest
import com.aamdigital.aambackendservice.common.couchdb.core.DefaultCouchDbClient
import com.aamdigital.aambackendservice.common.domain.FileStorage
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient
import java.time.Duration

/**
 * Spring configuration that creates the CouchDB [RestClient], [CouchDbClient],
 * [FileStorage], and [CouchDbInitializer] beans.
 *
 * There are two CouchDB clients because the two kinds of work want opposite read timeouts:
 *
 * - `couch-db-client` serves [DefaultCouchDbClient]: small documents, `_all_docs` ranges and
 *   `_changes` batches. Change detection runs on this client, so it must fail fast rather than
 *   stall the poll thread while CouchDB is unresponsive.
 * - `couch-db-attachment-client` serves [CouchDbFileStorage], which streams report-calculation
 *   attachments. On download the CouchDB response stream is relayed to the HTTP caller, so a
 *   caller that stops reading shows up here as an idle socket — which is exactly what a read
 *   timeout measures. It therefore keeps the much larger timeout this path had before timeouts
 *   were configured at all.
 *
 * Read timeouts are socket timeouts, bounding a single blocking read rather than the whole
 * transfer, so a steadily streaming attachment never trips one.
 */
@Configuration
class CouchDbConfiguration {
    @Bean
    fun couchDbFileStorage(
        @Qualifier("couch-db-attachment-client") restClient: RestClient,
        objectMapper: ObjectMapper
    ): FileStorage = CouchDbFileStorage(restClient, objectMapper)

    @Bean
    fun defaultCouchDbStorage(
        @Qualifier("couch-db-client") restClient: RestClient,
        objectMapper: ObjectMapper
    ): CouchDbClient = DefaultCouchDbClient(restClient, objectMapper)

    @Bean
    fun couchDbInitializer(
        couchDbClient: CouchDbClient,
        databaseRequests: List<DatabaseRequest>
    ): CouchDbInitializer = CouchDbInitializer(couchDbClient = couchDbClient, databaseRequests = databaseRequests)

    @Bean(name = ["couch-db-client"])
    fun couchDbWebClient(
        restTemplateBuilder: RestClient.Builder = RestClient.builder(),
        configuration: CouchDbClientConfiguration
    ): RestClient =
        couchDbRestClient(
            builder = restTemplateBuilder,
            configuration = configuration,
            readTimeoutInSeconds = configuration.responseTimeoutInSeconds
        )

    @Bean(name = ["couch-db-attachment-client"])
    fun couchDbAttachmentWebClient(
        restTemplateBuilder: RestClient.Builder = RestClient.builder(),
        configuration: CouchDbClientConfiguration
    ): RestClient =
        couchDbRestClient(
            builder = restTemplateBuilder,
            configuration = configuration,
            readTimeoutInSeconds = configuration.attachmentResponseTimeoutInSeconds
        )

    /**
     * Builds a CouchDB client with explicit timeouts.
     *
     * The request factory is created the same way Spring Boot's auto-configured builder already
     * does it ([ClientHttpRequestFactoryBuilder.detect]), so only the timeouts differ from the
     * defaults and the underlying HTTP transport is unchanged.
     */
    private fun couchDbRestClient(
        builder: RestClient.Builder,
        configuration: CouchDbClientConfiguration,
        readTimeoutInSeconds: Int
    ): RestClient {
        var basePath = configuration.basePath
        if (!basePath.endsWith("/")) {
            basePath = "$basePath/"
        }

        val requestFactory =
            ClientHttpRequestFactoryBuilder
                .detect()
                .build(
                    ClientHttpRequestFactorySettings
                        .defaults()
                        .withConnectTimeout(Duration.ofSeconds(configuration.connectTimeoutInSeconds.toLong()))
                        .withReadTimeout(Duration.ofSeconds(readTimeoutInSeconds.toLong()))
                )

        return builder
            .baseUrl(basePath)
            .requestFactory(requestFactory)
            .defaultHeaders {
                it.setBasicAuth(
                    configuration.basicAuthUsername,
                    configuration.basicAuthPassword
                )
            }.build()
    }
}

/** Externalized connection properties for the CouchDB HTTP clients. */
@ConfigurationProperties("couch-db-client-configuration")
class CouchDbClientConfiguration(
    val basePath: String,
    val basicAuthUsername: String,
    val basicAuthPassword: String,
    /**
     * TCP connect timeout. CouchDB is a same-network service, so a connect taking longer than this
     * means a dead or unreachable host rather than a slow one. The HTTP client's own default is 3
     * minutes, which would stall the change-detection poll thread on every request while CouchDB
     * is down.
     */
    val connectTimeoutInSeconds: Int = 5,
    /**
     * Read timeout for the JSON client. Well above any legitimate wait for the small documents,
     * `_all_docs` ranges and `_changes` batches it fetches — the changes feed is `feed=normal`, so
     * there is no long poll to accommodate.
     */
    val responseTimeoutInSeconds: Int = 30,
    /**
     * Read timeout for the attachment client. Deliberately much larger, because a caller that
     * stops reading a download idles the CouchDB socket here. This matches the HTTP client's own
     * socket-timeout default, i.e. what this path had before timeouts were configured.
     */
    val attachmentResponseTimeoutInSeconds: Int = 180
)
