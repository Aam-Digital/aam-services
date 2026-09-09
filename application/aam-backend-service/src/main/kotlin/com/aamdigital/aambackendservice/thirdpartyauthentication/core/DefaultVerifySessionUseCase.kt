package com.aamdigital.aambackendservice.thirdpartyauthentication.core

import com.aamdigital.aambackendservice.common.domain.UseCaseOutcome
import com.aamdigital.aambackendservice.common.error.AamErrorCode
import com.aamdigital.aambackendservice.thirdpartyauthentication.VerifySessionUseCase
import com.aamdigital.aambackendservice.thirdpartyauthentication.VerifySessionUseCaseData
import com.aamdigital.aambackendservice.thirdpartyauthentication.VerifySessionUseCaseRequest
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.Instant
import java.time.ZoneOffset

class DefaultVerifySessionUseCase(
    private val authenticationSessionStore: AuthenticationSessionStore,
    private val passwordEncoder: PasswordEncoder
) : VerifySessionUseCase() {
    enum class DefaultVerifySessionUseCaseError : AamErrorCode {
        INVALID_SESSION,
        INVALID_SESSION_TOKEN,
        SESSION_ALREADY_USED,
        SESSION_EXPIRED
    }

    override fun apply(request: VerifySessionUseCaseRequest): UseCaseOutcome<VerifySessionUseCaseData> {
        val session =
            authenticationSessionStore.find(request.sessionId)
                ?: return UseCaseOutcome.Failure(
                    errorCode = DefaultVerifySessionUseCaseError.INVALID_SESSION,
                    errorMessage = "Invalid credentials"
                )

        if (!passwordEncoder.matches(request.sessionToken, session.sessionTokenHash)) {
            return UseCaseOutcome.Failure(
                errorCode = DefaultVerifySessionUseCaseError.INVALID_SESSION_TOKEN,
                errorMessage = "Invalid credentials"
            )
        }

        if (session.usedAt != null) {
            return UseCaseOutcome.Failure(
                errorCode = DefaultVerifySessionUseCaseError.SESSION_ALREADY_USED,
                errorMessage = "Invalid credentials"
            )
        }

        val now = Instant.now().atOffset(ZoneOffset.UTC)

        if (now.isAfter(session.validUntil)) {
            return UseCaseOutcome.Failure(
                errorCode = DefaultVerifySessionUseCaseError.SESSION_EXPIRED,
                errorMessage = "Invalid credentials"
            )
        }

        authenticationSessionStore.markUsed(session.sessionId, now)

        return UseCaseOutcome.Success(
            data =
                VerifySessionUseCaseData(
                    userId = session.userId
                )
        )
    }
}
