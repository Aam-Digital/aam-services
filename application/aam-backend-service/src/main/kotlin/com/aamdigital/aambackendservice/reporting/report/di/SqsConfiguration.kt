package com.aamdigital.aambackendservice.reporting.report.di

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient

@Configuration
class SqsConfiguration {
    @Bean(name = ["sqs-client"])
    fun sqsRestClient(configuration: SqsClientConfiguration): RestClient {
        val clientBuilder = RestClient.builder()
            .baseUrl(configuration.basePath)
            .defaultHeaders {
                it.setBasicAuth(
                    configuration.basicAuthUsername,
                    configuration.basicAuthPassword,
                )
            }

        return clientBuilder.build()
    }
}

@ConfigurationProperties("sqs-client-configuration")
class SqsClientConfiguration(
    val basePath: String,
    val basicAuthUsername: String,
    val basicAuthPassword: String,
)
