package com.aamdigital.aambackendservice.apiclient.core

import com.aamdigital.aambackendservice.apiclient.core.ApiClientAdministration.Companion.CLIENT_ID_PREFIX
import com.aamdigital.aambackendservice.apiclient.domain.ApiClient
import com.aamdigital.aambackendservice.apiclient.domain.ApiClientCredentials
import com.aamdigital.aambackendservice.common.error.AamException
import com.aamdigital.aambackendservice.common.error.ExternalSystemException
import com.aamdigital.aambackendservice.common.error.ForbiddenAccessException
import com.aamdigital.aambackendservice.common.error.InvalidArgumentException
import com.aamdigital.aambackendservice.common.error.NotFoundException
import jakarta.ws.rs.ForbiddenException
import jakarta.ws.rs.ProcessingException
import jakarta.ws.rs.WebApplicationException
import org.keycloak.admin.client.CreatedResponseUtil
import org.keycloak.admin.client.Keycloak
import org.keycloak.admin.client.resource.ClientResource
import org.keycloak.representations.idm.ClientRepresentation
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Instant

/**
 * [ApiClientAdministration] through the Keycloak Admin REST API.
 *
 * The service account of the backend's own Keycloak client needs the `realm-management` role `manage-clients`.
 * That role allows changing every client of the realm, so the clients created here are marked with
 * [MANAGED_ATTRIBUTE] and [CLIENT_ID_PREFIX], and any other client is treated as if it did not exist.
 */
class KeycloakApiClientAdministration(
    private val keycloak: Keycloak,
    private val realm: String,
    private val backendClientId: String,
    private val clock: Clock = Clock.systemUTC()
) : ApiClientAdministration {
    companion object {
        const val MANAGED_ATTRIBUTE = "aam.api-client.managed"
        const val CREATED_BY_ATTRIBUTE = "aam.api-client.created-by"
        const val CREATED_AT_ATTRIBUTE = "aam.api-client.created-at"
        private const val DESCRIPTION = "API access of an external system, managed in the Aam Digital app"
        private const val HTTP_CREATED = 201
        private const val HTTP_CONFLICT = 409
        private const val HTTP_FORBIDDEN = 403
    }

    private val logger = LoggerFactory.getLogger(javaClass)

    private val realmResource get() = keycloak.realm(realm)

    override fun findApiClients(): List<ApiClient> =
        call("list API clients") {
            realmResource
                .clients()
                .findAll()
                .filter { it.isApiClient() }
                .map { it.toApiClient() }
        }

    override fun createApiClient(newApiClient: NewApiClient): ApiClientCredentials =
        call("create API client '${newApiClient.clientId}'") {
            require(newApiClient.clientId.startsWith(CLIENT_ID_PREFIX)) {
                "The client id of an API client has to start with '$CLIENT_ID_PREFIX'"
            }
            val scopeIds = findClientScopeIds(newApiClient.scopes)
            val clientResource = realmResource.clients().get(createClient(newApiClient))

            try {
                scopeIds.forEach { clientResource.addDefaultClientScope(it) }
                ApiClientCredentials(clientId = newApiClient.clientId, clientSecret = clientResource.secret.value)
            } catch (ex: Exception) {
                // nobody got the credentials of the client, so it must not stay behind half set up
                removeAfterFailure(clientResource, newApiClient.clientId, ex)
                throw ex
            }
        }

    override fun regenerateSecret(clientId: String): ApiClientCredentials =
        call("regenerate the secret of API client '$clientId'") {
            val client = findApiClientRepresentation(clientId)
            val secret = realmResource.clients().get(client.id).generateNewSecret()
            ApiClientCredentials(clientId = clientId, clientSecret = secret.value)
        }

    override fun deleteApiClient(clientId: String) =
        call("delete API client '$clientId'") {
            val client = findApiClientRepresentation(clientId)
            realmResource.clients().get(client.id).remove()
        }

    private fun createClient(newApiClient: NewApiClient): String =
        realmResource.clients().create(newApiClient.toRepresentation()).use { response ->
            when (response.status) {
                HTTP_CREATED -> {
                    CreatedResponseUtil.getCreatedId(response)
                }

                HTTP_CONFLICT -> {
                    throw InvalidArgumentException(
                        message = "A client with the client id '${newApiClient.clientId}' already exists",
                        code = ApiClientAdministrationError.API_CLIENT_ALREADY_EXISTS
                    )
                }

                HTTP_FORBIDDEN -> {
                    throw ForbiddenException()
                }

                else -> {
                    throw ExternalSystemException(
                        message = "Keycloak responded with HTTP ${response.status} to create a client",
                        code = ApiClientAdministrationError.KEYCLOAK_REQUEST_FAILED
                    )
                }
            }
        }

    private fun findClientScopeIds(names: Set<String>): List<String> {
        val clientScopes = realmResource.clientScopes().findAll().associateBy { it.name }
        return names.map { name ->
            clientScopes[name]?.id
                ?: throw NotFoundException(
                    message =
                        "Client scope '$name' does not exist in realm '$realm'. The backend creates it on " +
                            "startup if it has the permission to; otherwise create it in the Keycloak admin console.",
                    code = ApiClientAdministrationError.CLIENT_SCOPE_NOT_FOUND
                )
        }
    }

    private fun findApiClientRepresentation(clientId: String): ClientRepresentation =
        realmResource.clients().findByClientId(clientId).singleOrNull { it.isApiClient() }
            ?: throw NotFoundException(
                message = "There is no API client '$clientId'",
                code = ApiClientAdministrationError.API_CLIENT_NOT_FOUND
            )

    private fun removeAfterFailure(
        clientResource: ClientResource,
        clientId: String,
        cause: Exception
    ) {
        try {
            clientResource.remove()
        } catch (ex: Exception) {
            cause.addSuppressed(ex)
            logger.error(
                "Could not remove the Keycloak client '{}' after failing to set it up. Delete it in the " +
                    "Keycloak admin console.",
                clientId
            )
        }
    }

    private fun NewApiClient.toRepresentation() =
        ClientRepresentation().also {
            it.clientId = clientId
            it.name = name
            it.description = DESCRIPTION
            it.protocol = "openid-connect"
            it.isEnabled = true
            it.isPublicClient = false
            it.isBearerOnly = false
            it.clientAuthenticatorType = "client-secret"
            it.isServiceAccountsEnabled = true
            it.isStandardFlowEnabled = false
            it.isImplicitFlowEnabled = false
            it.isDirectAccessGrantsEnabled = false
            it.redirectUris = emptyList()
            it.webOrigins = emptyList()
            it.attributes =
                listOfNotNull(
                    MANAGED_ATTRIBUTE to "true",
                    CREATED_AT_ATTRIBUTE to Instant.now(clock).toString(),
                    createdBy?.let { createdBy -> CREATED_BY_ATTRIBUTE to createdBy }
                ).toMap()
        }

    private fun ClientRepresentation.isApiClient() =
        clientId.startsWith(CLIENT_ID_PREFIX) && attributes?.get(MANAGED_ATTRIBUTE) == "true"

    private fun ClientRepresentation.toApiClient() =
        ApiClient(
            clientId = clientId,
            name = name ?: clientId,
            scopes =
                realmResource
                    .clients()
                    .get(id)
                    .defaultClientScopes
                    .map { it.name }
                    .toSet(),
            createdBy = attributes?.get(CREATED_BY_ATTRIBUTE),
            createdAt = attributes?.get(CREATED_AT_ATTRIBUTE)
        )

    private fun <T> call(
        action: String,
        block: () -> T
    ): T =
        try {
            block()
        } catch (ex: AamException) {
            throw ex
        } catch (ex: ForbiddenException) {
            throw ForbiddenAccessException(
                message =
                    "Keycloak denied to $action in realm '$realm'. Assign the realm-management role " +
                        "'manage-clients' to the service account of Keycloak client '$backendClientId'.",
                cause = ex,
                code = ApiClientAdministrationError.MISSING_KEYCLOAK_PERMISSION
            )
        } catch (ex: WebApplicationException) {
            throw ExternalSystemException(
                message = "Keycloak responded with HTTP ${ex.response.status} to $action in realm '$realm'",
                cause = ex,
                code = ApiClientAdministrationError.KEYCLOAK_REQUEST_FAILED
            )
        } catch (ex: ProcessingException) {
            throw ExternalSystemException(
                message = "Could not reach Keycloak to $action in realm '$realm'",
                cause = ex,
                code = ApiClientAdministrationError.KEYCLOAK_REQUEST_FAILED
            )
        }
}
