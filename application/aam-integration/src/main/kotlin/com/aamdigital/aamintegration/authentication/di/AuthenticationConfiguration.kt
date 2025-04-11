package com.aamdigital.aamintegration.authentication.di

import com.aamdigital.aamintegration.authentication.core.AuthenticationProvider
import com.aamdigital.aamintegration.authentication.core.CreateSessionUseCase
import com.aamdigital.aamintegration.authentication.core.DefaultCreateSessionUseCase
import com.aamdigital.aamintegration.authentication.core.DefaultSessionRedirectUseCase
import com.aamdigital.aamintegration.authentication.core.DefaultVerifySessionUseCase
import com.aamdigital.aamintegration.authentication.core.SessionRedirectUseCase
import com.aamdigital.aamintegration.authentication.core.VerifySessionUseCase
import com.aamdigital.aamintegration.authentication.repository.AuthenticationSessionRepository
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.crypto.factory.PasswordEncoderFactories
import org.springframework.security.crypto.password.PasswordEncoder

@Configuration
class AuthenticationConfiguration {

    @Bean
    fun passwordEncoder(): PasswordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder()

    @Bean
    fun defaultCreateSessionUseCase(
        authenticationSessionRepository: AuthenticationSessionRepository,
        passwordEncoder: PasswordEncoder,
        authenticationProvider: AuthenticationProvider,
    ): CreateSessionUseCase = DefaultCreateSessionUseCase(
        authenticationSessionRepository = authenticationSessionRepository,
        passwordEncoder = passwordEncoder,
        authenticationProvider = authenticationProvider,
    )

    @Bean
    fun defaultSessionRedirectUseCase(
        authenticationSessionRepository: AuthenticationSessionRepository
    ): SessionRedirectUseCase = DefaultSessionRedirectUseCase(
        authenticationSessionRepository = authenticationSessionRepository,
    )

    @Bean
    fun defaultVerifySessionUseCase(
        authenticationSessionRepository: AuthenticationSessionRepository,
        passwordEncoder: PasswordEncoder,
    ): VerifySessionUseCase = DefaultVerifySessionUseCase(
        authenticationSessionRepository = authenticationSessionRepository,
        passwordEncoder = passwordEncoder,
    )
}
