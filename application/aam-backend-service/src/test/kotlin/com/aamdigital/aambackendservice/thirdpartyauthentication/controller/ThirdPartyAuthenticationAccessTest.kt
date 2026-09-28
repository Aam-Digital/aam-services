package com.aamdigital.aambackendservice.thirdpartyauthentication.controller

import com.aamdigital.aambackendservice.common.domain.ApplicationConfig
import com.aamdigital.aambackendservice.common.domain.DomainUseCase
import com.aamdigital.aambackendservice.common.domain.UseCaseOutcome
import com.aamdigital.aambackendservice.common.security.AamAuthorities
import com.aamdigital.aambackendservice.thirdpartyauthentication.CreateSessionUseCase
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig

@SpringJUnitConfig(ThirdPartyAuthenticationAccessTest.Config::class)
class ThirdPartyAuthenticationAccessTest {
    @Configuration
    @EnableMethodSecurity
    class Config {
        @Bean
        fun thirdPartyAuthenticationController() =
            ThirdPartyAuthenticationController(
                createSessionUseCase =
                    mock<CreateSessionUseCase> {
                        on { run(any()) } doReturn
                            UseCaseOutcome.Failure(DomainUseCase.DomainError.UNHANDLED_EXCEPTION_IN_USE_CASE)
                    },
                verifySessionUseCase = mock(),
                sessionRedirectUseCase = mock(),
                applicationConfig = mock<ApplicationConfig>()
            )
    }

    @Autowired
    private lateinit var controller: ThirdPartyAuthenticationController

    private fun startSession() =
        controller.startSession(
            UserSessionRequest(userId = "user-1", firstName = "First", lastName = "Last", email = "user@example.com")
        )

    @Test
    @WithMockUser(authorities = ["SCOPE_third_party_authentication"])
    fun `should allow starting a session with the third_party_authentication scope`() {
        // When
        val response = startSession()

        // Then
        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
    }

    @Test
    @WithMockUser(authorities = ["ROLE_third-party-authentication-provider", "SCOPE_reporting_write"])
    fun `should deny starting a session with only the legacy realm role`() {
        // When / Then
        assertThatThrownBy { startSession() }.isInstanceOf(AccessDeniedException::class.java)
    }

    @Test
    @WithMockUser(authorities = [AamAuthorities.FRONTEND_CLIENT])
    fun `should deny starting a session for users of the frontend app`() {
        // When / Then
        assertThatThrownBy { startSession() }.isInstanceOf(AccessDeniedException::class.java)
    }
}
