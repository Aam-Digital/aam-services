package com.aamdigital.aambackendservice.report.di

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient

@Configuration
class SqsConfiguration {
    @Bean(name = ["sqs-client"])
    fun sqsWebClient(configuration: SqsClientConfiguration): WebClient {
        val clientBuilder =
            WebClient.builder().baseUrl(configuration.basePath)
                .defaultHeaders {
                    it.setBasicAuth(
                        configuration.basicAuthUsername,
                        configuration.basicAuthPassword,
                    )
                }
        return clientBuilder.clientConnector(ReactorClientHttpConnector(HttpClient.create())).build()
    }
}

@ConfigurationProperties("sqs-client-configuration")
class SqsClientConfiguration(
    val basePath: String,
    val basicAuthUsername: String,
    val basicAuthPassword: String,
)
