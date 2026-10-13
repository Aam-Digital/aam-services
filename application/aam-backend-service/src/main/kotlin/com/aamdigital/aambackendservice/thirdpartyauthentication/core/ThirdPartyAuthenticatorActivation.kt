package com.aamdigital.aambackendservice.thirdpartyauthentication.core

import jakarta.ws.rs.ForbiddenException
import org.keycloak.admin.client.Keycloak
import org.keycloak.admin.client.resource.AuthenticationManagementResource
import org.keycloak.representations.idm.AuthenticationExecutionInfoRepresentation
import org.keycloak.representations.idm.AuthenticatorConfigRepresentation
import org.slf4j.LoggerFactory

/**
 * Adds the third-party authenticator (a Keycloak provider in the keycloak-aam image) to the realm's login flow,
 * switches it on and points it to this backend, so that users of an external system can be signed in by a session
 * created through the third-party-authentication API.
 *
 * The login flow itself (e.g. with email 2FA) is defined by the deployment (e.g. ndb-setup's realm template).
 * This only adds its own step, directly after the cookie step, and never removes or reorders any other step.
 * Keycloak's built-in login flow cannot be changed, so a realm that logs in with it is only reported.
 * Nothing is switched off when the module is disabled.
 *
 * The service account of the backend's Keycloak client needs the `realm-management` role `manage-realm`.
 * This is best effort, like the client scope setup: a failure is logged as a warning.
 *
 * @param apiBaseUrl the URL of this backend's API, which the authenticator calls to validate a session
 */
class ThirdPartyAuthenticatorActivation(
    private val keycloak: Keycloak,
    private val realm: String,
    private val apiBaseUrl: String
) {
    companion object {
        const val PROVIDER_ID = "third-party-authenticator"
        private const val COOKIE_PROVIDER_ID = "auth-cookie"
        const val CONFIG_ALIAS = "aam-third-party-authentication"
        const val API_BASE_URL_PROPERTY = "third-party-api-base-url"
        private const val ALTERNATIVE = "ALTERNATIVE"
        private const val HTTP_CREATED = 201
        private const val HTTP_FORBIDDEN = 403
    }

    private val logger = LoggerFactory.getLogger(javaClass)

    fun activate() {
        try {
            val realmResource = keycloak.realm(realm)
            val flows = realmResource.flows()
            val loginFlow = realmResource.toRepresentation().browserFlow

            if (flows.flows.any { it.alias == loginFlow && it.isBuiltIn }) {
                logger.warn(
                    "Keycloak realm '{}' logs in with the built-in flow '{}', which cannot be changed, so the " +
                        "third-party authenticator cannot be added and users of external systems cannot be " +
                        "signed in. Bind the realm's own login flow (as the deployment's realm setup does).",
                    realm,
                    loginFlow
                )
                return
            }

            addStep(flows, loginFlow)
            // configured first: if that fails, the step stays disabled instead of pointing to a wrong API
            configure(flows, findSteps(flows, loginFlow).first())
            // read again for the update: Keycloak also applies the step's priority, which adding it changed
            enable(flows, loginFlow, findSteps(flows, loginFlow).first())
        } catch (ex: ForbiddenException) {
            logger.warn(
                "Keycloak denied to set up the third-party authenticator in realm '{}'. Assign the " +
                    "realm-management role 'manage-realm' to the backend's service account. Until then, " +
                    "users of external systems cannot be signed in.",
                realm,
                ex
            )
        } catch (ex: Exception) {
            logger.warn("Could not set up the third-party authenticator in Keycloak realm '{}'", realm, ex)
        }
    }

    private fun findSteps(
        flows: AuthenticationManagementResource,
        loginFlow: String
    ) = flows.getExecutions(loginFlow).filter { it.level == 0 && it.providerId == PROVIDER_ID }

    /**
     * Adds the step if the flow has none yet (Keycloak adds a new step disabled), removes any further one
     * (e.g. added by another instance starting at the same time) and moves it up to directly after the cookie step.
     * A step that is further up already stays where it is.
     */
    private fun addStep(
        flows: AuthenticationManagementResource,
        loginFlow: String
    ) {
        if (findSteps(flows, loginFlow).isEmpty()) {
            flows.addExecution(loginFlow, mapOf("provider" to PROVIDER_ID))
            logger.info("Added the third-party authenticator to the login flow '{}' of realm '{}'", loginFlow, realm)
        }
        // Keycloak allows the same step more than once in a flow: two instances starting at the same time could
        // both have added it. Keep the first one and remove any further copy.
        findSteps(flows, loginFlow).drop(1).forEach { flows.removeExecution(it.id) }

        val topLevel = flows.getExecutions(loginFlow).filter { it.level == 0 }
        val current = topLevel.indexOfFirst { it.providerId == PROVIDER_ID }
        val target = topLevel.indexOfFirst { it.providerId == COOKIE_PROVIDER_ID } + 1
        repeat(current - target) { flows.raisePriority(topLevel[current].id) }
    }

    private fun enable(
        flows: AuthenticationManagementResource,
        loginFlow: String,
        step: AuthenticationExecutionInfoRepresentation
    ) {
        if (step.requirement == ALTERNATIVE) {
            return
        }
        step.requirement = ALTERNATIVE
        flows.updateExecutions(loginFlow, step)
        logger.info("Enabled the third-party authenticator in the login flow '{}' of realm '{}'", loginFlow, realm)
    }

    private fun configure(
        flows: AuthenticationManagementResource,
        step: AuthenticationExecutionInfoRepresentation
    ) {
        val configId = step.authenticationConfig
        if (configId == null) {
            val config =
                AuthenticatorConfigRepresentation().apply {
                    alias = CONFIG_ALIAS
                    this.config = mapOf(API_BASE_URL_PROPERTY to apiBaseUrl)
                }
            flows.newExecutionConfig(step.id, config).use { response ->
                when (response.status) {
                    HTTP_CREATED -> Unit
                    HTTP_FORBIDDEN -> throw ForbiddenException()
                    else -> error("Keycloak responded with HTTP ${response.status} to configure the step")
                }
            }
            logger.info("Configured the third-party authenticator in realm '{}' with API {}", realm, apiBaseUrl)
            return
        }

        val config = flows.getAuthenticatorConfig(configId)
        if (config.config?.get(API_BASE_URL_PROPERTY) == apiBaseUrl) {
            return
        }
        config.config = (config.config ?: emptyMap()) + (API_BASE_URL_PROPERTY to apiBaseUrl)
        flows.updateAuthenticatorConfig(configId, config)
        logger.info("Pointed the third-party authenticator in realm '{}' to API {}", realm, apiBaseUrl)
    }
}
