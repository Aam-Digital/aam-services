package com.aamdigital.aamintegration.security

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain

@Configuration
@EnableWebSecurity
class SecurityConfiguration {

    @Bean
    fun filterChain(
        http: HttpSecurity,
        aamAuthenticationConverter: AamAuthenticationConverter,
        aamAccessDeniedHandler: AamAccessDeniedHandler,
        objectMapper: ObjectMapper,
    ): SecurityFilterChain {
        http {
            authorizeHttpRequests {
                authorize(HttpMethod.GET, "/", permitAll)
                authorize(HttpMethod.GET, "/actuator/health", permitAll)
                authorize(HttpMethod.GET, "/actuator/health/liveness", permitAll)
                authorize(HttpMethod.GET, "/actuator/health/readiness", permitAll)
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
                        parentEntryPoint = BearerTokenAuthenticationEntryPoint(),
                        objectMapper = objectMapper
                    )
            }
            oauth2ResourceServer {
                jwt {
                    jwtAuthenticationConverter = aamAuthenticationConverter
                    authenticationEntryPoint =
                        AamAuthenticationEntryPoint(
                            parentEntryPoint = BearerTokenAuthenticationEntryPoint(),
                            objectMapper = objectMapper
                        )
                }
            }
        }
        return http.build()
    }
}
