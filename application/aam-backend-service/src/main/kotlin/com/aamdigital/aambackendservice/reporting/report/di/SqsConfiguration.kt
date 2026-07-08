package com.aamdigital.aambackendservice.reporting.report.di

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.web.client.RestClient

@Configuration
class SqsConfiguration {
    @Bean(name = ["sqs-client"])
    fun sqsRestClient(configuration: SqsClientConfiguration): RestClient {
        val clientBuilder =
            RestClient
                .builder()
                .baseUrl(configuration.basePath)
                .defaultHeaders {
                    it.setBasicAuth(
                        configuration.basicAuthUsername,
                        configuration.basicAuthPassword
                    )
                    // SQS's Node server intermittently resets reused keep-alive connections when a
                    // long, event-loop-blocking query follows a quickly answered one, discarding the
                    // response after computing it (backend then sees "Connection reset"). Closing the
                    // connection after every exchange avoids reuse on both sides; with multi-second
                    // query times the extra TCP handshake per request is negligible.
                    it.set(HttpHeaders.CONNECTION, "close")
                }

        return clientBuilder.build()
    }
}

@ConfigurationProperties("sqs-client-configuration")
class SqsClientConfiguration(
    val basePath: String,
    val basicAuthUsername: String,
    val basicAuthPassword: String
)
