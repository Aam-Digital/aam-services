package com.aamdigital.aambackendservice.thirdpartyauthentication.core

import com.aamdigital.aambackendservice.domain.DomainUseCase
import com.aamdigital.aambackendservice.domain.UseCaseData
import com.aamdigital.aambackendservice.domain.UseCaseRequest

data class VerifySessionUseCaseRequest(
    val sessionId: String,
    val sessionToken: String,
) : UseCaseRequest

data class VerifySessionUseCaseData(
    val userId: String,
) : UseCaseData

abstract class VerifySessionUseCase : DomainUseCase<VerifySessionUseCaseRequest, VerifySessionUseCaseData>()
