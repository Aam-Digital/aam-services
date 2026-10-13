package com.aamdigital.aambackendservice.apiclient.controller

import com.aamdigital.aambackendservice.apiclient.ConditionalOnApiClientEnabled
import com.aamdigital.aambackendservice.apiclient.RequiresApiClientAdministrationAccess
import com.aamdigital.aambackendservice.apiclient.core.ApiClientAdministration
import com.aamdigital.aambackendservice.apiclient.core.ApiClientAdministrationError
import com.aamdigital.aambackendservice.apiclient.core.CreateApiClientError
import com.aamdigital.aambackendservice.apiclient.core.CreateApiClientRequest
import com.aamdigital.aambackendservice.apiclient.core.CreateApiClientUseCase
import com.aamdigital.aambackendservice.apiclient.domain.ApiClient
import com.aamdigital.aambackendservice.apiclient.domain.ApiClientCredentials
import com.aamdigital.aambackendservice.common.domain.UseCaseOutcome
import com.aamdigital.aambackendservice.common.error.AamErrorCode
import com.aamdigital.aambackendservice.common.error.AamException
import com.aamdigital.aambackendservice.common.error.HttpErrorDto
import com.aamdigital.aambackendservice.common.keycloak.core.ClientScopeRequest
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

data class ApiClientScopeDto(
    val name: String,
    val description: String
)

data class ApiClientDto(
    val clientId: String,
    val name: String,
    val scopes: List<String>,
    val createdBy: String?,
    val createdAt: String?
)

data class CreateApiClientRequestDto(
    val name: String,
    val scopes: List<String>
)

data class ApiClientCredentialsDto(
    val clientId: String,
    val clientSecret: String
)

/**
 * Self-service management of the API clients (Keycloak clients) that external systems use to access the API,
 * for the administrators of the app who also manage its user accounts.
 */
@RestController
@RequestMapping("/v1/api-client")
@ConditionalOnApiClientEnabled
@RequiresApiClientAdministrationAccess
class ApiClientController(
    private val apiClientAdministration: ApiClientAdministration,
    private val createApiClientUseCase: CreateApiClientUseCase,
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
    ): ResponseEntity<Any> {
        val outcome =
            createApiClientUseCase.run(
                CreateApiClientRequest(
                    name = request.name,
                    scopes = request.scopes.toSet(),
                    createdBy = authentication.userLabel()
                )
            )

        return when (outcome) {
            is UseCaseOutcome.Success -> {
                ResponseEntity.status(HttpStatus.CREATED).body(outcome.data.credentials.toDto())
            }

            is UseCaseOutcome.Failure -> {
                errorResponse("create", outcome.errorCode, outcome.errorMessage, outcome.cause)
            }
        }
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
            CreateApiClientError.INVALID_NAME,
            CreateApiClientError.NO_SCOPE,
            CreateApiClientError.UNKNOWN_SCOPE -> HttpStatus.BAD_REQUEST

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
