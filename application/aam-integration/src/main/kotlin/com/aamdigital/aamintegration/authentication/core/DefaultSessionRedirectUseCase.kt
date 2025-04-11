package com.aamdigital.aamintegration.authentication.core

import com.aamdigital.aamintegration.authentication.repository.AuthenticationSessionRepository
import com.aamdigital.aamintegration.domain.UseCaseOutcome
import com.aamdigital.aamintegration.error.AamErrorCode
import kotlin.jvm.optionals.getOrNull

class DefaultSessionRedirectUseCase(
    private val authenticationSessionRepository: AuthenticationSessionRepository,
) : SessionRedirectUseCase() {

    enum class DefaultSessionRedirectUseCaseError : AamErrorCode {
        INVALID_SESSION,
        INVALID_USER_SESSION,
        NO_REDIRECT_FOUND
    }

    override fun apply(request: SessionRedirectUseCaseRequest): UseCaseOutcome<SessionRedirectUseCaseData> {
        val session = authenticationSessionRepository.findByExternalIdentifier(request.sessionId).getOrNull()
            ?: return UseCaseOutcome.Failure(
                errorCode = DefaultSessionRedirectUseCaseError.INVALID_SESSION,
                errorMessage = "Invalid credentials"
            )

        if (session.userId != request.userId) {
            return UseCaseOutcome.Failure(
                errorCode = DefaultSessionRedirectUseCaseError.INVALID_USER_SESSION,
                errorMessage = "Session is not valid for this user"
            )
        }

        val redirectUrl = session.redirectUrl

        return if (redirectUrl.isNullOrBlank()) {
            UseCaseOutcome.Failure(
                errorCode = DefaultSessionRedirectUseCaseError.NO_REDIRECT_FOUND,
                errorMessage = "No redirect url found"
            )
        } else {
            UseCaseOutcome.Success(
                data = SessionRedirectUseCaseData(
                    redirectUrl = redirectUrl
                )
            )
        }
    }
}
