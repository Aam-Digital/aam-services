package com.aamdigital.aambackendservice.common.security

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.ProviderManager
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtDecoders
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider
import org.springframework.security.oauth2.server.resource.authentication.JwtIssuerAuthenticationManagerResolver
import org.springframework.security.web.SecurityFilterChain

@Configuration
@EnableWebSecurity
class SecurityConfiguration(
    @Value("\${aam-security.allowed-issuers:#{null}}")
    private val configuredAllowedIssuers: List<String>?,
) {

    private val allowedIssuers: List<String>
        get() = configuredAllowedIssuers ?: listOf(
            "https://keycloak.aam-digital.net",
            "https://keycloak.aam-digital.com",
            "https://auth.aam-digital.app",
            "https://auth.aam-digital.dev",
            "https://id.aam-digital.app",
            "https://id.aam-digital.dev",
        )

    @Bean
    fun filterChain(
        http: HttpSecurity,
        aamAuthenticationConverter: AamAuthenticationConverter,
        aamAccessDeniedHandler: AamAccessDeniedHandler,
        objectMapper: ObjectMapper,
        jwtIssuerAuthenticationManagerResolver: JwtIssuerAuthenticationManagerResolver,
    ): SecurityFilterChain {
        http {
            authorizeRequests {
                authorize(HttpMethod.GET, "/", permitAll)
                authorize(HttpMethod.GET, "/actuator/health", permitAll)
                authorize(HttpMethod.GET, "/actuator/health/liveness", permitAll)
                authorize(HttpMethod.GET, "/actuator/health/readiness", permitAll)
                authorize(HttpMethod.GET, "/actuator/features", permitAll)
                authorize(HttpMethod.GET, "/v1/third-party-authentication/session/*", permitAll)
                authorize(anyRequest, authenticated)
            }
            httpBasic {
                disable()
            }
            csrf {
                disable()
            }
            formLogin {
                disable()
            }
            sessionManagement {
                sessionCreationPolicy = SessionCreationPolicy.STATELESS
            }
            exceptionHandling {
                accessDeniedHandler = aamAccessDeniedHandler
                authenticationEntryPoint =
                    AamAuthenticationEntryPoint(
                        objectMapper = objectMapper
                    )
            }
            oauth2ResourceServer {
                authenticationManagerResolver = jwtIssuerAuthenticationManagerResolver
                authenticationEntryPoint =
                    AamAuthenticationEntryPoint(
                        objectMapper = objectMapper
                    )
            }
        }
        return http.build()
    }

    @Bean
    fun authenticationManagerResolver(
        aamAuthenticationConverter: AamAuthenticationConverter
    ): JwtIssuerAuthenticationManagerResolver {
        return JwtIssuerAuthenticationManagerResolver { issuer ->
            if (allowedIssuers.any { issuer.startsWith(it) }.not()) {
                throw BadCredentialsException("Untrusted issuer: $issuer")
            }

            val decoder: JwtDecoder = JwtDecoders.fromIssuerLocation(issuer)
            val provider = JwtAuthenticationProvider(decoder)
            provider.setJwtAuthenticationConverter(aamAuthenticationConverter)

            ProviderManager(provider)
        }
    }
}
