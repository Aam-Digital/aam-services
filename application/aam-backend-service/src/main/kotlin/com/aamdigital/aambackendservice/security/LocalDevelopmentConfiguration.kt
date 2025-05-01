package com.aamdigital.aambackendservice.security

import org.springframework.boot.ssl.SslBundles
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestTemplate
import java.net.HttpURLConnection
import javax.net.ssl.HttpsURLConnection

/**
 * This Configuration will configure the self-signed ssl certificate from the caddy reverse proxy as trusted,
 * when the local-development profile is active.
 *
 * Do not enable this in production!
 */
@Configuration
class LocalDevelopmentConfiguration {
    @Bean
    @Profile("local-development")
    fun restTemplate(restTemplateBuilder: RestTemplateBuilder, sslBundles: SslBundles): RestTemplate {
        return restTemplateBuilder.setSslBundle(sslBundles.getBundle("local-development")).build()
    }

    @Bean
    @Profile("local-development")
    fun restClientBuilder(
        sslBundles: SslBundles
    ): RestClient.Builder {
        return RestClient.builder()
            .requestFactory(object : SimpleClientHttpRequestFactory() {
                override fun prepareConnection(connection: HttpURLConnection, httpMethod: String) {
                    if (connection is HttpsURLConnection) {
                        try {
                            val context = sslBundles.getBundle("local-development").createSslContext()
                            connection.sslSocketFactory = context.socketFactory
                        } catch (ex: Exception) {
                            // nothing
                        }
                    }

                    super.prepareConnection(connection, httpMethod)
                }
            })
    }

    @Bean
    @Profile("local-development")
    fun sslCheckDisabledJwtDecoder(
        restTemplate: RestTemplate
    ): JwtDecoder {
        return NimbusJwtDecoder
            .withIssuerLocation(
                "http://localhost:8080/realms/dummy-realm"
            )
            .restOperations(restTemplate)
            .build()
    }
}
