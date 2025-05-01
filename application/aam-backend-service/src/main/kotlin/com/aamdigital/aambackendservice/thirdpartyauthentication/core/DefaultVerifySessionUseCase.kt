package com.aamdigital.aambackendservice.thirdpartyauthentication.core

import com.aamdigital.aambackendservice.domain.UseCaseOutcome
import com.aamdigital.aambackendservice.error.AamErrorCode
import com.aamdigital.aambackendservice.thirdpartyauthentication.VerifySessionUseCase
import com.aamdigital.aambackendservice.thirdpartyauthentication.VerifySessionUseCaseData
import com.aamdigital.aambackendservice.thirdpartyauthentication.VerifySessionUseCaseRequest
import com.aamdigital.aambackendservice.thirdpartyauthentication.repository.AuthenticationSessionRepository
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.Instant
import java.time.ZoneOffset
import kotlin.jvm.optionals.getOrNull

class DefaultVerifySessionUseCase(
    private val authenticationSessionRepository: AuthenticationSessionRepository,
    private val passwordEncoder: PasswordEncoder,
) : VerifySessionUseCase() {

    enum class DefaultVerifySessionUseCaseError : AamErrorCode {
        INVALID_SESSION,
        INVALID_SESSION_TOKEN,
        SESSION_ALREADY_USED,
        SESSION_EXPIRED,
    }

    override fun apply(request: VerifySessionUseCaseRequest): UseCaseOutcome<VerifySessionUseCaseData> {
        val session = authenticationSessionRepository.findByExternalIdentifier(request.sessionId).getOrNull()
            ?: return UseCaseOutcome.Failure(
                errorCode = DefaultVerifySessionUseCaseError.INVALID_SESSION,
                errorMessage = "Invalid credentials"
            )

        if (!passwordEncoder.matches(request.sessionToken, session.sessionToken)) {
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

        if (Instant.now().atOffset(ZoneOffset.UTC).isAfter(session.validUntil)) {
            return UseCaseOutcome.Failure(
                errorCode = DefaultVerifySessionUseCaseError.SESSION_EXPIRED,
                errorMessage = "Invalid credentials"
            )
        }

        session.usedAt = Instant.now().atOffset(ZoneOffset.UTC)

        authenticationSessionRepository.save(session)

        return UseCaseOutcome.Success(
            data = VerifySessionUseCaseData(
                userId = session.userId
            )
        )

    }
}
