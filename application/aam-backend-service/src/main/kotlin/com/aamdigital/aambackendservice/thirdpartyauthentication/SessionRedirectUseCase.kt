package com.aamdigital.aambackendservice.thirdpartyauthentication

import com.aamdigital.aambackendservice.domain.DomainUseCase
import com.aamdigital.aambackendservice.domain.UseCaseData
import com.aamdigital.aambackendservice.domain.UseCaseRequest

data class SessionRedirectUseCaseRequest(
    val sessionId: String,
    val userId: String,
) : UseCaseRequest

data class SessionRedirectUseCaseData(
    val redirectUrl: String,
) : UseCaseData

abstract class SessionRedirectUseCase : DomainUseCase<SessionRedirectUseCaseRequest, SessionRedirectUseCaseData>()
