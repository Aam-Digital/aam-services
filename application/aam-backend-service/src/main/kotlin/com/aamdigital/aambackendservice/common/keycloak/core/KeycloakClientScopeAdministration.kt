package com.aamdigital.aambackendservice.common.keycloak.core

import com.aamdigital.aambackendservice.common.error.AamErrorCode
import com.aamdigital.aambackendservice.common.error.ExternalSystemException
import com.aamdigital.aambackendservice.common.error.ForbiddenAccessException
import jakarta.ws.rs.ForbiddenException
import org.keycloak.admin.client.Keycloak
import org.keycloak.admin.client.resource.ClientResource
import org.keycloak.representations.idm.ClientScopeRepresentation
import org.slf4j.LoggerFactory

enum class KeycloakClientScopeAdministrationError : AamErrorCode {
    MISSING_KEYCLOAK_PERMISSION,
    KEYCLOAK_REQUEST_FAILED
}

/**
 * [ClientScopeAdministration] through the Keycloak Admin REST API.
 *
 * The service account of the backend's own Keycloak client needs the `realm-management` roles
 * `manage-clients` (client scopes and their assignment), `view-users` (role mappings of service accounts)
 * and `manage-realm` (deleting a replaced realm role).
 */
class KeycloakClientScopeAdministration(
    private val keycloak: Keycloak,
    private val realm: String,
    private val backendClientId: String
) : ClientScopeAdministration {
    companion object {
        private const val INCLUDE_IN_TOKEN_SCOPE = "include.in.token.scope"
        private const val HTTP_CREATED = 201
        private const val HTTP_CONFLICT = 409
        private const val HTTP_FORBIDDEN = 403
    }

    private val logger = LoggerFactory.getLogger(javaClass)

    private val realmResource get() = keycloak.realm(realm)

    override fun findClientScopes(): List<KeycloakClientScope> =
        call("list client scopes") {
            realmResource.clientScopes().findAll().map { it.toClientScope() }
        }

    override fun createClientScope(
        name: String,
        description: String
    ) = call("create client scope '$name'") {
        val representation =
            ClientScopeRepresentation().apply {
                this.name = name
                this.description = description
                this.protocol = "openid-connect"
                this.attributes =
                    mapOf(
                        INCLUDE_IN_TOKEN_SCOPE to "true",
                        "display.on.consent.screen" to "false"
                    )
            }

        realmResource.clientScopes().create(representation).use { response ->
            when (response.status) {
                // conflict: created concurrently in the meantime, which is just as good
                HTTP_CREATED, HTTP_CONFLICT -> Unit

                HTTP_FORBIDDEN -> throw ForbiddenException()

                else -> throw ExternalSystemException(
                    message = "Keycloak responded with HTTP ${response.status} to create client scope '$name'",
                    code = KeycloakClientScopeAdministrationError.KEYCLOAK_REQUEST_FAILED
                )
            }
        }
    }

    override fun findServiceAccountClients(): List<KeycloakServiceAccountClient> =
        call("list clients") {
            realmResource
                .clients()
                .findAll()
                .filter { it.isServiceAccountsEnabled == true }
                .map { client ->
                    val clientResource = realmResource.clients().get(client.id)
                    KeycloakServiceAccountClient(
                        id = client.id,
                        clientId = client.clientId,
                        defaultClientScopes = clientResource.defaultClientScopes.map { it.name }.toSet(),
                        optionalClientScopes = clientResource.optionalClientScopes.map { it.name }.toSet()
                    )
                }
        }

    /**
     * Keycloak ignores adding a Default scope that the client already has as Optional scope,
     * so the Optional assignment is removed first (and restored if adding the Default scope fails).
     */
    override fun assignDefaultClientScope(
        client: KeycloakServiceAccountClient,
        scope: KeycloakClientScope
    ) = call("assign client scope '${scope.name}' to client '${client.clientId}'") {
        val clientResource = realmResource.clients().get(client.id)
        val isOptional = scope.name in client.optionalClientScopes

        if (isOptional) {
            clientResource.removeOptionalClientScope(scope.id)
        }
        try {
            clientResource.addDefaultClientScope(scope.id)
        } catch (ex: Exception) {
            if (isOptional) {
                restoreOptionalClientScope(clientResource, client, scope, ex)
            }
            throw ex
        }
    }

    private fun restoreOptionalClientScope(
        clientResource: ClientResource,
        client: KeycloakServiceAccountClient,
        scope: KeycloakClientScope,
        cause: Exception
    ) {
        try {
            clientResource.addOptionalClientScope(scope.id)
        } catch (ex: Exception) {
            cause.addSuppressed(ex)
            // the client has neither the Default nor the Optional assignment now, and no later startup restores it
            logger.error(
                "Keycloak client '{}' lost client scope '{}'. Assign it as Default client scope in the Keycloak " +
                    "admin console, otherwise the client is denied access.",
                client.clientId,
                scope.name
            )
        }
    }

    override fun realmRoleExists(roleName: String): Boolean =
        call("find realm role '$roleName'") {
            realmResource.roles().list(roleName, true).any { it.name == roleName }
        }

    override fun serviceAccountHasRealmRole(
        client: KeycloakServiceAccountClient,
        roleName: String
    ): Boolean =
        call("read realm roles of the service account of client '${client.clientId}'") {
            val serviceAccountUser = realmResource.clients().get(client.id).serviceAccountUser
            realmResource
                .users()
                .get(serviceAccountUser.id)
                .roles()
                .realmLevel()
                .listEffective()
                .any { it.name == roleName }
        }

    override fun deleteRealmRole(roleName: String) =
        call("delete realm role '$roleName'") {
            realmResource.roles().deleteRole(roleName)
        }

    private fun <T> call(
        action: String,
        block: () -> T
    ): T =
        try {
            block()
        } catch (ex: ForbiddenException) {
            throw ForbiddenAccessException(
                message =
                    "Keycloak denied to $action in realm '$realm'. " +
                        "Assign the realm-management roles 'manage-clients', 'view-users' and 'manage-realm' " +
                        "to the service account of Keycloak client '$backendClientId'. Until then, client scopes " +
                        "are neither created nor migrated, and API clients relying on them are denied access.",
                cause = ex,
                code = KeycloakClientScopeAdministrationError.MISSING_KEYCLOAK_PERMISSION
            )
        }

    private fun ClientScopeRepresentation.toClientScope() =
        KeycloakClientScope(
            id = id,
            name = name,
            // Keycloak treats a missing attribute as enabled
            includedInTokenScope = attributes?.get(INCLUDE_IN_TOKEN_SCOPE)?.toBoolean() ?: true
        )
}
