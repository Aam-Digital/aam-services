package com.aamdigital.aambackendservice.thirdpartyauthentication

import com.aamdigital.aambackendservice.domain.DomainUseCase
import com.aamdigital.aambackendservice.domain.UseCaseData
import com.aamdigital.aambackendservice.domain.UseCaseRequest
import java.time.OffsetDateTime

data class CreateSessionUseCaseRequest(
    val userId: String,
    val firstName: String,
    val lastName: String,
    val redirectUrl: String? = null,
    val email: String,
    val additionalData: Map<String, String>,
) : UseCaseRequest

data class CreateSessionUseCaseData(
    val sessionId: String,
    val sessionToken: String,
    val entryPointUrl: String,
    val validUntil: OffsetDateTime,
) : UseCaseData

abstract class CreateSessionUseCase : DomainUseCase<CreateSessionUseCaseRequest, CreateSessionUseCaseData>()
