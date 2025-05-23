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
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient

@Configuration
class CouchDbConfiguration {

    @Bean
    fun couchDbFileStorage(
        @Qualifier("couch-db-client") restClient: RestClient,
        objectMapper: ObjectMapper,
    ): FileStorage = CouchDbFileStorage(restClient, objectMapper)

    @Bean
    fun defaultCouchDbStorage(
        @Qualifier("couch-db-client") restClient: RestClient,
        objectMapper: ObjectMapper,
    ): CouchDbClient = DefaultCouchDbClient(restClient, objectMapper)

    @Bean
    fun couchDbInitializer(
        couchDbClient: CouchDbClient,
        databaseRequests: List<DatabaseRequest>,
    ): CouchDbInitializer =
        CouchDbInitializer(couchDbClient = couchDbClient, databaseRequests = databaseRequests)

    @Bean(name = ["couch-db-client"])
    fun couchDbWebClient(
        restTemplateBuilder: RestClient.Builder = RestClient.builder(),
        configuration: CouchDbClientConfiguration
    ): RestClient {
        var basePath = configuration.basePath
        if (!basePath.endsWith("/")) {
            basePath = "$basePath/"
        }

        val clientBuilder = restTemplateBuilder
            .baseUrl(basePath)
            .defaultHeaders {
                it.setBasicAuth(
                    configuration.basicAuthUsername,
                    configuration.basicAuthPassword,
                )
            }

        return clientBuilder.build()
    }
}

@ConfigurationProperties("couch-db-client-configuration")
class CouchDbClientConfiguration(
    val basePath: String,
    val basicAuthUsername: String,
    val basicAuthPassword: String,
)
