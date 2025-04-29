package com.aamdigital.aambackendservice.thirdpartyauthentication.di

import com.aamdigital.aambackendservice.thirdpartyauthentication.core.AuthenticationProvider
import com.aamdigital.aambackendservice.thirdpartyauthentication.core.CreateSessionUseCase
import com.aamdigital.aambackendservice.thirdpartyauthentication.core.DefaultCreateSessionUseCase
import com.aamdigital.aambackendservice.thirdpartyauthentication.core.DefaultSessionRedirectUseCase
import com.aamdigital.aambackendservice.thirdpartyauthentication.core.DefaultVerifySessionUseCase
import com.aamdigital.aambackendservice.thirdpartyauthentication.core.SessionRedirectUseCase
import com.aamdigital.aambackendservice.thirdpartyauthentication.core.VerifySessionUseCase
import com.aamdigital.aambackendservice.thirdpartyauthentication.repository.AuthenticationSessionRepository
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
