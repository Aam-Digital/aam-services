package com.aamdigital.aambackendservice.security

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
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
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http {
            authorizeRequests {
                authorize(HttpMethod.GET, "/", permitAll)
                authorize(HttpMethod.GET, "/actuator", permitAll)
                authorize(HttpMethod.GET, "/actuator/**", permitAll)
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
                authenticationEntryPoint =
                    AamAuthenticationEntryPoint(
                        parentEntryPoint = BearerTokenAuthenticationEntryPoint(),
                        objectMapper = jacksonObjectMapper()
                    )
            }
            oauth2ResourceServer {
                jwt {
                    authenticationEntryPoint =
                        AamAuthenticationEntryPoint(
                            parentEntryPoint = BearerTokenAuthenticationEntryPoint(),
                            objectMapper = jacksonObjectMapper()
                        )
                }
            }
        }
        return http.build()
    }


}

