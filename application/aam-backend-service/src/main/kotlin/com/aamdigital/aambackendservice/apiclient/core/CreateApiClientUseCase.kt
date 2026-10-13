package com.aamdigital.aambackendservice.apiclient.core

import com.aamdigital.aambackendservice.apiclient.domain.ApiClientCredentials
import com.aamdigital.aambackendservice.common.domain.DomainUseCase
import com.aamdigital.aambackendservice.common.domain.UseCaseData
import com.aamdigital.aambackendservice.common.domain.UseCaseRequest
import com.aamdigital.aambackendservice.common.error.AamErrorCode

/**
 * @property name label of the API client, e.g. the name of the external system
 * @property scopes the client scopes to give the API client
 * @property userRealmRoles realm roles of the user creating the API client, which decide the scopes it may give
 * @property createdBy the user creating the API client
 */
data class CreateApiClientRequest(
    val name: String,
    val scopes: Set<String>,
    val userRealmRoles: Set<String>,
    val createdBy: String?
) : UseCaseRequest

data class CreateApiClientData(
    val credentials: ApiClientCredentials,
    val scopes: Set<String>
) : UseCaseData

enum class CreateApiClientError : AamErrorCode {
    INVALID_NAME,
    NO_SCOPE,
    UNKNOWN_SCOPE,
    SCOPE_NOT_PERMITTED
}

/**
 * Create an API client for an external system, with the client scopes the requesting user is allowed to give.
 */
abstract class CreateApiClientUseCase : DomainUseCase<CreateApiClientRequest, CreateApiClientData>()
