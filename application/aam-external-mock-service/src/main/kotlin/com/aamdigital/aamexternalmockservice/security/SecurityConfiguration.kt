package com.aamdigital.aamexternalmockservice.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain

@Configuration
@EnableWebSecurity
class SecurityConfiguration {

  @Bean
  @Order(Ordered.LOWEST_PRECEDENCE)
  fun filterChain(http: HttpSecurity): SecurityFilterChain {
    http {
      authorizeRequests {
        authorize(HttpMethod.GET, "/", permitAll)
        authorize(HttpMethod.GET, "/actuator", permitAll)
        authorize(HttpMethod.GET, "/actuator/**", permitAll)
        authorize(anyRequest, denyAll)
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
    }
    return http.build()
  }


}
