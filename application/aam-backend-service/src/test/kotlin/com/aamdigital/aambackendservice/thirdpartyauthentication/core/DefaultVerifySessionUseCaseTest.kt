package com.aamdigital.aambackendservice.thirdpartyauthentication.core

import com.aamdigital.aambackendservice.common.domain.UseCaseOutcome
import com.aamdigital.aambackendservice.thirdpartyauthentication.VerifySessionUseCaseRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.security.crypto.factory.PasswordEncoderFactories
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * Covers the ticket lifecycle branches directly, because the e2e feature cannot reach the expiry
 * one without waiting out the validity window.
 *
 * Expiry is decided by [AuthenticationSession.validUntil], not by cache eviction: the store keeps
 * entries around for longer than they are valid precisely so an expired ticket can still be
 * reported as SESSION_EXPIRED rather than as an unknown session.
 */
class DefaultVerifySessionUseCaseTest {
    private val passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder()
    private lateinit var store: AuthenticationSessionStore
    private lateinit var useCase: DefaultVerifySessionUseCase

    private val sessionToken = "the-session-token"

    @BeforeEach
    fun setUp() {
        store = CaffeineAuthenticationSessionStore(sessionValidity = Duration.ofMinutes(5))
        useCase =
            DefaultVerifySessionUseCase(
                authenticationSessionStore = store,
                passwordEncoder = passwordEncoder
            )
    }

    private fun storeSession(
        sessionId: String = "session-1",
        validUntil: OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(5)
    ) {
        store.store(
            AuthenticationSession(
                sessionId = sessionId,
                userId = "keycloak-user-1",
                externalUserId = "external-user-1",
                sessionTokenHash = passwordEncoder.encode(sessionToken),
                validUntil = validUntil
            )
        )
    }

    @Test
    fun `returns the linked user for a valid ticket`() {
        storeSession()

        val outcome = useCase.run(VerifySessionUseCaseRequest("session-1", sessionToken))

        assertThat(outcome).isInstanceOf(UseCaseOutcome.Success::class.java)
        assertThat((outcome as UseCaseOutcome.Success).data.userId).isEqualTo("keycloak-user-1")
    }

    @Test
    fun `rejects an unknown session`() {
        val outcome = useCase.run(VerifySessionUseCaseRequest("no-such-session", sessionToken))

        assertThat(outcome).isInstanceOf(UseCaseOutcome.Failure::class.java)
        assertThat((outcome as UseCaseOutcome.Failure).errorCode)
            .isEqualTo(DefaultVerifySessionUseCase.DefaultVerifySessionUseCaseError.INVALID_SESSION)
    }

    @Test
    fun `rejects a wrong session token`() {
        storeSession()

        val outcome = useCase.run(VerifySessionUseCaseRequest("session-1", "not-the-token"))

        assertThat((outcome as UseCaseOutcome.Failure).errorCode)
            .isEqualTo(DefaultVerifySessionUseCase.DefaultVerifySessionUseCaseError.INVALID_SESSION_TOKEN)
    }

    @Test
    fun `rejects a ticket that was already redeemed`() {
        storeSession()
        useCase.run(VerifySessionUseCaseRequest("session-1", sessionToken))

        val outcome = useCase.run(VerifySessionUseCaseRequest("session-1", sessionToken))

        assertThat((outcome as UseCaseOutcome.Failure).errorCode)
            .isEqualTo(DefaultVerifySessionUseCase.DefaultVerifySessionUseCaseError.SESSION_ALREADY_USED)
    }

    @Test
    fun `rejects a ticket whose validity has passed`() {
        storeSession(validUntil = Instant.now().minusSeconds(1).atOffset(ZoneOffset.UTC))

        val outcome = useCase.run(VerifySessionUseCaseRequest("session-1", sessionToken))

        assertThat((outcome as UseCaseOutcome.Failure).errorCode)
            .isEqualTo(DefaultVerifySessionUseCase.DefaultVerifySessionUseCaseError.SESSION_EXPIRED)
    }
}
