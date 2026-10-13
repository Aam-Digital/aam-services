package com.aamdigital.aambackendservice.admin.apiclient.core

import com.aamdigital.aambackendservice.admin.apiclient.domain.ApiClientCredentials
import com.aamdigital.aambackendservice.common.domain.DomainUseCase
import com.aamdigital.aambackendservice.common.domain.UseCaseData
import com.aamdigital.aambackendservice.common.domain.UseCaseRequest
import com.aamdigital.aambackendservice.common.error.AamErrorCode

/**
 * @property name label of the API client, e.g. the name of the external system
 * @property scopes the client scopes to give the API client
 * @property createdBy the user creating the API client
 */
data class CreateApiClientRequest(
    val name: String,
    val scopes: Set<String>,
    val createdBy: String?
) : UseCaseRequest

data class CreateApiClientData(
    val credentials: ApiClientCredentials,
    val scopes: Set<String>
) : UseCaseData

enum class CreateApiClientError : AamErrorCode {
    INVALID_NAME,
    NO_SCOPE,
    UNKNOWN_SCOPE
}

/**
 * Create an API client for an external system, with some of the client scopes offered to API clients.
 */
abstract class CreateApiClientUseCase : DomainUseCase<CreateApiClientRequest, CreateApiClientData>()
