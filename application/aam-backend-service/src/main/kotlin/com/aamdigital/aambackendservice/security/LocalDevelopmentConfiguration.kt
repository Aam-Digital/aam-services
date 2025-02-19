package com.aamdigital.aambackendservice.security

import org.springframework.boot.ssl.SslBundles
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.web.client.RestTemplate

@Configuration
class LocalDevelopmentConfiguration {
    @Bean
    @Profile("local-development")
    fun restTemplate(restTemplateBuilder: RestTemplateBuilder, sslBundles: SslBundles): RestTemplate {
        return restTemplateBuilder.setSslBundle(sslBundles.getBundle("local-development")).build()
    }

    @Bean
    @Profile("local-development")
    fun sslCheckDisabledJwtDecoder(
        restTemplate: RestTemplate
    ): JwtDecoder {
        return NimbusJwtDecoder
            .withIssuerLocation(
                "https://aam.localhost/auth/realms/dummy-realm"
            )
            .restOperations(restTemplate)
            .build()
    }
}
