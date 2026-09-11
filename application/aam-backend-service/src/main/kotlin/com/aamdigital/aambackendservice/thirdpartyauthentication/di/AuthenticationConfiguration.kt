package com.aamdigital.aambackendservice.thirdpartyauthentication.di

import com.aamdigital.aambackendservice.common.couchdb.core.CouchDbClient
import com.aamdigital.aambackendservice.thirdpartyauthentication.ConditionalOnThirdPartyAuthenticationEnabled
import com.aamdigital.aambackendservice.thirdpartyauthentication.CreateSessionUseCase
import com.aamdigital.aambackendservice.thirdpartyauthentication.SessionRedirectUseCase
import com.aamdigital.aambackendservice.thirdpartyauthentication.VerifySessionUseCase
import com.aamdigital.aambackendservice.thirdpartyauthentication.core.AuthenticationProvider
import com.aamdigital.aambackendservice.thirdpartyauthentication.core.AuthenticationSessionStore
import com.aamdigital.aambackendservice.thirdpartyauthentication.core.CaffeineAuthenticationSessionStore
import com.aamdigital.aambackendservice.thirdpartyauthentication.core.DefaultCreateSessionUseCase
import com.aamdigital.aambackendservice.thirdpartyauthentication.core.DefaultSessionRedirectUseCase
import com.aamdigital.aambackendservice.thirdpartyauthentication.core.DefaultVerifySessionUseCase
import com.aamdigital.aambackendservice.thirdpartyauthentication.repository.CouchDbThirdPartyAuthSessionRepository
import com.aamdigital.aambackendservice.thirdpartyauthentication.repository.ThirdPartyAuthSessionRepository
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.crypto.factory.PasswordEncoderFactories
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.Duration

@ConditionalOnThirdPartyAuthenticationEnabled
@Configuration
class AuthenticationConfiguration {
    companion object {
        /** How long a login ticket can be redeemed after it was issued. */
        val SESSION_VALIDITY: Duration = Duration.ofMinutes(5)
    }

    @Bean
    fun passwordEncoder(): PasswordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder()

    @Bean
    fun authenticationSessionStore(): AuthenticationSessionStore =
        CaffeineAuthenticationSessionStore(sessionValidity = SESSION_VALIDITY)

    @Bean
    fun thirdPartyAuthSessionRepository(couchDbClient: CouchDbClient): ThirdPartyAuthSessionRepository =
        CouchDbThirdPartyAuthSessionRepository(couchDbClient = couchDbClient)

    @Bean
    fun defaultCreateSessionUseCase(
        authenticationSessionStore: AuthenticationSessionStore,
        thirdPartyAuthSessionRepository: ThirdPartyAuthSessionRepository,
        passwordEncoder: PasswordEncoder,
        authenticationProvider: AuthenticationProvider,
        couchDbClient: CouchDbClient
    ): CreateSessionUseCase =
        DefaultCreateSessionUseCase(
            authenticationSessionStore = authenticationSessionStore,
            thirdPartyAuthSessionRepository = thirdPartyAuthSessionRepository,
            passwordEncoder = passwordEncoder,
            authenticationProvider = authenticationProvider,
            couchDbClient = couchDbClient,
            sessionValidity = SESSION_VALIDITY
        )

    @Bean
    fun defaultSessionRedirectUseCase(
        thirdPartyAuthSessionRepository: ThirdPartyAuthSessionRepository
    ): SessionRedirectUseCase =
        DefaultSessionRedirectUseCase(
            thirdPartyAuthSessionRepository = thirdPartyAuthSessionRepository
        )

    @Bean
    fun defaultVerifySessionUseCase(
        authenticationSessionStore: AuthenticationSessionStore,
        passwordEncoder: PasswordEncoder
    ): VerifySessionUseCase =
        DefaultVerifySessionUseCase(
            authenticationSessionStore = authenticationSessionStore,
            passwordEncoder = passwordEncoder
        )
}
