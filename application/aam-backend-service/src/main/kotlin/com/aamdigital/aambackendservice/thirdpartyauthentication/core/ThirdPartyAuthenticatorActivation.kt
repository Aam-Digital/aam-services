package com.aamdigital.aambackendservice.thirdpartyauthentication.core

import jakarta.ws.rs.ForbiddenException
import org.keycloak.admin.client.Keycloak
import org.keycloak.admin.client.resource.AuthenticationManagementResource
import org.keycloak.representations.idm.AuthenticationExecutionInfoRepresentation
import org.keycloak.representations.idm.AuthenticatorConfigRepresentation
import org.slf4j.LoggerFactory

/**
 * Switches on the third-party authenticator (a Keycloak provider in the keycloak-aam image) in the realm's login
 * flow and points it to this backend, so that users of an external system can be signed in by a session
 * created through the third-party-authentication API.
 *
 * The deployment (e.g. ndb-setup) adds the step to the login flow, disabled. This only changes its requirement
 * to ALTERNATIVE and its configuration; it never adds, moves or removes a step, so a realm without the step is
 * only reported. Nothing is switched off when the module is disabled.
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
        const val CONFIG_ALIAS = "aam-third-party-authentication"
        const val API_BASE_URL_PROPERTY = "third-party-api-base-url"
        private const val ALTERNATIVE = "ALTERNATIVE"
        private const val HTTP_CREATED = 201
        private const val HTTP_FORBIDDEN = 403
    }

    private val logger = LoggerFactory.getLogger(javaClass)

    fun activate() {
        try {
            val flows = keycloak.realm(realm).flows()
            val loginFlow = keycloak.realm(realm).toRepresentation().browserFlow
            val step =
                flows.getExecutions(loginFlow).firstOrNull { it.level == 0 && it.providerId == PROVIDER_ID }

            if (step == null) {
                logger.warn(
                    "The login flow '{}' of Keycloak realm '{}' has no third-party authenticator, so users of " +
                        "external systems cannot be signed in. Add it with the deployment's realm setup " +
                        "(ndb-setup).",
                    loginFlow,
                    realm
                )
                return
            }

            enable(flows, loginFlow, step)
            configure(flows, step)
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
