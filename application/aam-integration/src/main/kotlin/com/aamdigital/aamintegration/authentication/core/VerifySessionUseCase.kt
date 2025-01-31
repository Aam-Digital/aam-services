package com.aamdigital.aamintegration.authentication.core

import com.aamdigital.aamintegration.domain.DomainUseCase
import com.aamdigital.aamintegration.domain.UseCaseData
import com.aamdigital.aamintegration.domain.UseCaseRequest

data class VerifySessionUseCaseRequest(
    val sessionId: String,
    val sessionToken: String,
) : UseCaseRequest

data class VerifySessionUseCaseData(
    val userId: String,
) : UseCaseData

abstract class VerifySessionUseCase : DomainUseCase<VerifySessionUseCaseRequest, VerifySessionUseCaseData>()
