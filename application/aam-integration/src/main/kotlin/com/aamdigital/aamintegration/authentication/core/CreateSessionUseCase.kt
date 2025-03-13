package com.aamdigital.aamintegration.authentication.core

import com.aamdigital.aamintegration.domain.DomainUseCase
import com.aamdigital.aamintegration.domain.UseCaseData
import com.aamdigital.aamintegration.domain.UseCaseRequest
import java.time.OffsetDateTime

data class CreateSessionUseCaseRequest(
    val realmId: String,
    val userId: String,
    val firstName: String,
    val lastName: String,
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
