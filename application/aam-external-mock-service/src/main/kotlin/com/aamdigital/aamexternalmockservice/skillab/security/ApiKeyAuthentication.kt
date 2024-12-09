package com.aamdigital.aamexternalmockservice.skillab.security

import com.aamdigital.aamexternalmockservice.security.ApiKeyAuthentication
import com.aamdigital.aamexternalmockservice.skillab.error.RestErrorHandler
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import jakarta.servlet.http.HttpServletRequest
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.AuthorityUtils
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.stereotype.Service

@ConfigurationProperties("skilllab-auth-config")
class SkillLabAuthConfig(
    val headerName: String,
    val headerValue: String,
)

@Service
class SkillLabAuthenticationService(
    val authConfig: SkillLabAuthConfig,
) {
    fun getAuthentication(request: HttpServletRequest): Authentication {
        val apiKey = request.getHeader(authConfig.headerName)
        if (apiKey == null || apiKey != authConfig.headerValue) {
            throw BadCredentialsException("Invalid API Key")
        }

        return ApiKeyAuthentication(apiKey, AuthorityUtils.NO_AUTHORITIES)
    }
}

@Configuration
class SkillLabSecurityConfiguration {

    @Bean
    @Order(1)
    fun skillLabSecurityChain(
        http: HttpSecurity,
        restErrorHandler: RestErrorHandler,
        skillLabAuthenticationService: SkillLabAuthenticationService,
    ): SecurityFilterChain {
        http {
            securityMatcher("/skilllab/**")
            authorizeRequests {
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
            addFilterBefore<UsernamePasswordAuthenticationFilter>(
                SkillLabAuthenticationFilter(
                    restErrorHandler = restErrorHandler,
                    objectMapper = jacksonObjectMapper(),
                    skillLabAuthenticationService = skillLabAuthenticationService
                )
            )
        }
        return http.build()
    }


}
