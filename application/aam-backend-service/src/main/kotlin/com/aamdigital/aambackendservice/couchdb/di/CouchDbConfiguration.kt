package com.aamdigital.aambackendservice.couchdb.di

import com.aamdigital.aambackendservice.couchdb.core.CouchDbClient
import com.aamdigital.aambackendservice.couchdb.core.CouchDbStorage
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient

@Configuration
class CouchDbConfiguration {

    @Bean
    fun defaultCouchDbStorage(
        @Qualifier("couch-db-client") webClient: WebClient,
        objectMapper: ObjectMapper,
    ): CouchDbStorage = CouchDbClient(webClient, objectMapper)

    @Bean(name = ["couch-db-client"])
    fun couchDbWebClient(configuration: CouchDbClientConfiguration): WebClient {
        val clientBuilder =
            WebClient.builder()
                .codecs {
                    it.defaultCodecs()
                        .maxInMemorySize(configuration.maxInMemorySizeInMegaBytes * 1024 * 1024)
                }
                .baseUrl(configuration.basePath)
                .defaultHeaders {
                    it.setBasicAuth(
                        configuration.basicAuthUsername,
                        configuration.basicAuthPassword,
                    )
                }
        return clientBuilder.clientConnector(ReactorClientHttpConnector(HttpClient.create())).build()
    }
}

@ConfigurationProperties("couch-db-client-configuration")
class CouchDbClientConfiguration(
    val basePath: String,
    val basicAuthUsername: String,
    val basicAuthPassword: String,
    val maxInMemorySizeInMegaBytes: Int = 16,
)
