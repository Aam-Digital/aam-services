package com.aamdigital.aamintegration.authentication.core

import com.aamdigital.aamintegration.domain.DomainUseCase
import com.aamdigital.aamintegration.domain.UseCaseData
import com.aamdigital.aamintegration.domain.UseCaseRequest

data class SessionRedirectUseCaseRequest(
    val sessionId: String,
    val userId: String,
) : UseCaseRequest

data class SessionRedirectUseCaseData(
    val redirectUrl: String,
) : UseCaseData

abstract class SessionRedirectUseCase : DomainUseCase<SessionRedirectUseCaseRequest, SessionRedirectUseCaseData>()
