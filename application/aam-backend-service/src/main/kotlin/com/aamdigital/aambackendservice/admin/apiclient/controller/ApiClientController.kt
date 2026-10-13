package com.aamdigital.aambackendservice.admin.apiclient.controller

import com.aamdigital.aambackendservice.admin.RequiresAdminAccess
import com.aamdigital.aambackendservice.admin.apiclient.core.ApiClientAdministration
import com.aamdigital.aambackendservice.admin.apiclient.core.ApiClientAdministrationError
import com.aamdigital.aambackendservice.admin.apiclient.domain.ApiClient
import com.aamdigital.aambackendservice.admin.apiclient.domain.ApiClientCredentials
import com.aamdigital.aambackendservice.common.error.AamErrorCode
import com.aamdigital.aambackendservice.common.error.AamException
import com.aamdigital.aambackendservice.common.error.HttpErrorDto
import com.aamdigital.aambackendservice.common.keycloak.core.ClientScopeRequest
import com.aamdigital.aambackendservice.common.keycloak.di.ConditionalOnKeycloakAdminAccess
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * A client scope that API clients can be given (response of `GET /v1/admin/api-client/scope`).
 *
 * @property name of the client scope, as it appears in the `scope` claim of the API client's access tokens
 * @property description of the access the scope grants
 */
data class ApiClientScopeDto(
    val name: String,
    val description: String
)

/**
 * An API client, i.e. a Keycloak client of an external system (response of `GET /v1/admin/api-client`).
 * Its secret is never part of it.
 *
 * @property clientId the id the external system authenticates with
 * @property name label given when creating it, e.g. the name of the external system
 * @property scopes the client scopes (of [ApiClientScopeDto]) the API client has
 * @property createdBy the user who created it (email, if their token has one)
 * @property createdAt ISO-8601 timestamp of the creation
 */
data class ApiClientDto(
    val clientId: String,
    val name: String,
    val scopes: List<String>,
    val createdBy: String?,
    val createdAt: String?
)

/**
 * Request body of `POST /v1/admin/api-client`.
 *
 * @property name label of the API client, e.g. the name of the external system (1 to 100 characters)
 * @property scopes client scopes (of [ApiClientScopeDto]) to give the API client; at least one
 */
data class CreateApiClientRequestDto(
    val name: String,
    val scopes: List<String>
)

/**
 * The credentials of an API client, for the external system to request access tokens with the client credentials
 * flow. Only returned when the API client is created or its secret is regenerated.
 *
 * @property clientId the id the external system authenticates with
 * @property clientSecret the secret the external system authenticates with
 */
data class ApiClientCredentialsDto(
    val clientId: String,
    val clientSecret: String
)

/**
 * Self-service management of the API clients (Keycloak clients) that external systems use to access the API,
 * for the administrators of the app who also manage its user accounts.
 */
@RestController
@RequestMapping("/v1/admin/api-client")
@ConditionalOnKeycloakAdminAccess
@RequiresAdminAccess
class ApiClientController(
    private val apiClientAdministration: ApiClientAdministration,
    /** the client scopes of the enabled feature modules, which are the ones offered to API clients */
    private val clientScopeRequests: List<ClientScopeRequest> = emptyList()
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @GetMapping("/scope")
    fun fetchScopes(): ResponseEntity<List<ApiClientScopeDto>> =
        ResponseEntity.ok(
            clientScopeRequests.map { ApiClientScopeDto(name = it.name, description = it.description) }
        )

    @GetMapping
    fun fetchApiClients(): ResponseEntity<Any> =
        handleErrors("list") {
            ResponseEntity.ok(apiClientAdministration.findApiClients().map { it.toDto() })
        }

    @PostMapping
    fun createApiClient(
        @RequestBody request: CreateApiClientRequestDto,
        authentication: JwtAuthenticationToken
    ): ResponseEntity<Any> =
        handleErrors("create") {
            val credentials =
                apiClientAdministration.createApiClient(
                    name = request.name,
                    scopes = request.scopes.toSet(),
                    createdBy = authentication.userLabel()
                )
            ResponseEntity.status(HttpStatus.CREATED).body(credentials.toDto())
        }

    @PostMapping("/{clientId}/secret")
    fun regenerateSecret(
        @PathVariable clientId: String
    ): ResponseEntity<Any> =
        handleErrors("regenerate the secret of") {
            ResponseEntity.ok(apiClientAdministration.regenerateSecret(clientId).toDto())
        }

    @DeleteMapping("/{clientId}")
    fun deleteApiClient(
        @PathVariable clientId: String
    ): ResponseEntity<Any> =
        handleErrors("delete") {
            apiClientAdministration.deleteApiClient(clientId)
            ResponseEntity.noContent().build()
        }

    private fun handleErrors(
        action: String,
        block: () -> ResponseEntity<Any>
    ): ResponseEntity<Any> =
        try {
            block()
        } catch (ex: AamException) {
            errorResponse(action, ex.code, ex.localizedMessage, ex)
        }

    private fun errorResponse(
        action: String,
        errorCode: AamErrorCode,
        errorMessage: String,
        cause: Throwable?
    ): ResponseEntity<Any> {
        val status = statusOf(errorCode)
        if (status.is5xxServerError) {
            logger.warn("Could not {} API client: [{}] {}", action, errorCode, errorMessage, cause)
        } else {
            logger.info("Could not {} API client: [{}] {}", action, errorCode, errorMessage)
        }
        return ResponseEntity.status(status).body(HttpErrorDto(errorCode.toString(), errorMessage))
    }

    private fun statusOf(errorCode: AamErrorCode): HttpStatus =
        when (errorCode) {
            ApiClientAdministrationError.INVALID_NAME,
            ApiClientAdministrationError.NO_SCOPE,
            ApiClientAdministrationError.UNKNOWN_SCOPE -> HttpStatus.BAD_REQUEST

            ApiClientAdministrationError.API_CLIENT_NOT_FOUND -> HttpStatus.NOT_FOUND

            ApiClientAdministrationError.API_CLIENT_ALREADY_EXISTS -> HttpStatus.CONFLICT

            else -> HttpStatus.INTERNAL_SERVER_ERROR
        }

    /** How the user is shown as the creator of an API client: by email, if the token has one */
    private fun JwtAuthenticationToken.userLabel(): String? =
        token.getClaimAsString("email")
            ?: token.getClaimAsString("preferred_username")
            ?: token.subject

    private fun ApiClient.toDto() =
        ApiClientDto(
            clientId = clientId,
            name = name,
            // only the scopes that grant access to this API, not Keycloak's own ones like "profile"
            scopes = scopes.filter { scope -> clientScopeRequests.any { it.name == scope } }.sorted(),
            createdBy = createdBy,
            createdAt = createdAt
        )

    private fun ApiClientCredentials.toDto() = ApiClientCredentialsDto(clientId = clientId, clientSecret = clientSecret)
}
