package com.aamdigital.aambackendservice.common.keycloak.core

import com.aamdigital.aambackendservice.common.error.ForbiddenAccessException
import org.slf4j.LoggerFactory

/**
 * Makes sure the client scopes required by the enabled feature modules exist in the Keycloak realm
 * (see [ClientScopeRequest]).
 *
 * This is best effort: each scope is handled independently, and a failure is logged as a warning
 * without stopping the application. Endpoints check the scopes of each access token regardless,
 * so a scope that could not be created here has to be created manually in Keycloak.
 * No client scope is ever deleted.
 */
class ClientScopeInitializer(
    private val administration: ClientScopeAdministration,
    private val clientScopeRequests: List<ClientScopeRequest>
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun initialize() {
        clientScopeRequests.forEach { request ->
            try {
                ensureClientScope(request)
            } catch (ex: ForbiddenAccessException) {
                logger.warn("Could not ensure Keycloak client scope '{}': {}", request.name, ex.message)
            } catch (ex: Exception) {
                logger.warn("Could not ensure Keycloak client scope '{}': {}", request.name, ex.message, ex)
            }
        }
    }

    private fun ensureClientScope(request: ClientScopeRequest) {
        val scope = findOrCreateClientScope(request)

        if (!scope.includedInTokenScope) {
            logger.warn(
                "Keycloak client scope '{}' has 'Include in token scope' disabled, " +
                    "so it never reaches access tokens and API clients are denied access. " +
                    "Enable it in the Keycloak admin console.",
                scope.name
            )
        }

        if (request.promoteOptionalToDefault) {
            promoteOptionalAssignments(scope)
        }
    }

    private fun findOrCreateClientScope(request: ClientScopeRequest): KeycloakClientScope {
        administration.findClientScopes().find { it.name == request.name }?.let { return it }

        logger.info("Keycloak client scope '{}' does not exist. Creating it.", request.name)
        administration.createClientScope(name = request.name, description = request.description)

        return administration.findClientScopes().find { it.name == request.name }
            ?: error("Keycloak client scope '${request.name}' not found after creating it")
    }

    private fun promoteOptionalAssignments(scope: KeycloakClientScope) {
        administration
            .findServiceAccountClients()
            .filter { scope.name in it.optionalClientScopes && scope.name !in it.defaultClientScopes }
            .forEach { client ->
                administration.assignDefaultClientScope(client, scope)
                logger.info(
                    "Changed Keycloak client scope '{}' of client '{}' from Optional to Default.",
                    scope.name,
                    client.clientId
                )
            }
    }
}
