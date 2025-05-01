package com.aamdigital.aambackendservice.thirdpartyauthentication.core

import com.aamdigital.aambackendservice.domain.UseCaseOutcome
import com.aamdigital.aambackendservice.error.AamErrorCode
import com.aamdigital.aambackendservice.thirdpartyauthentication.SessionRedirectUseCase
import com.aamdigital.aambackendservice.thirdpartyauthentication.SessionRedirectUseCaseData
import com.aamdigital.aambackendservice.thirdpartyauthentication.SessionRedirectUseCaseRequest
import com.aamdigital.aambackendservice.thirdpartyauthentication.repository.AuthenticationSessionRepository
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
