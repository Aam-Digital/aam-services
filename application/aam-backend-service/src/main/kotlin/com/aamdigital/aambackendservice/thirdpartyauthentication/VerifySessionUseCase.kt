package com.aamdigital.aambackendservice.thirdpartyauthentication

import com.aamdigital.aambackendservice.common.domain.DomainUseCase
import com.aamdigital.aambackendservice.common.domain.UseCaseData
import com.aamdigital.aambackendservice.common.domain.UseCaseRequest

data class VerifySessionUseCaseRequest(
    val sessionId: String,
    val sessionToken: String,
) : UseCaseRequest

data class VerifySessionUseCaseData(
    val userId: String,
) : UseCaseData

abstract class VerifySessionUseCase : DomainUseCase<VerifySessionUseCaseRequest, VerifySessionUseCaseData>()
