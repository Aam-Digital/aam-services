package com.aamdigital.aambackendservice.security

import com.aamdigital.aambackendservice.error.ForbiddenAccessException
import com.aamdigital.aambackendservice.error.UnauthorizedAccessException
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.security.web.server.ServerAuthenticationEntryPoint
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
class SecurityConfiguration {

    @Bean
    fun securityWebFilterChain(
        http: ServerHttpSecurity,
    ): SecurityWebFilterChain {
        return http
            .authorizeExchange {
                it.pathMatchers(HttpMethod.GET, "/").permitAll()
                it.pathMatchers(HttpMethod.GET, "/actuator").permitAll()
                it.pathMatchers(HttpMethod.GET, "/actuator/**").permitAll()
                it.anyExchange().authenticated()
            }
            .exceptionHandling {
                it.accessDeniedHandler(customServerAccessDeniedHandler())
                it.authenticationEntryPoint(CustomAuthenticationEntryPoint())
            }
            .oauth2ResourceServer {
                it.jwt {}
            }
            .build()
    }

    private fun customServerAccessDeniedHandler(): ServerAccessDeniedHandler {
        return ServerAccessDeniedHandler { _, denied ->
            throw ForbiddenAccessException(
                message = "Access Token not sufficient for operation",
                cause = denied
            )
        }
    }

    private class CustomAuthenticationEntryPoint : ServerAuthenticationEntryPoint {
        override fun commence(exchange: ServerWebExchange, ex: AuthenticationException): Mono<Void> {
            return Mono.fromRunnable {
                throw UnauthorizedAccessException("Access Token invalid or missing")
            }
        }
    }

}
