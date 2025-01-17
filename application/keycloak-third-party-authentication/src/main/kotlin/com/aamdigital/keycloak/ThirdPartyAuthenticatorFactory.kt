package com.aamdigital.keycloak

import lombok.extern.slf4j.Slf4j
import org.keycloak.Config
import org.keycloak.authentication.Authenticator
import org.keycloak.authentication.AuthenticatorFactory
import org.keycloak.authentication.ConfigurableAuthenticatorFactory.REQUIREMENT_CHOICES
import org.keycloak.models.AuthenticationExecutionModel
import org.keycloak.models.KeycloakSession
import org.keycloak.models.KeycloakSessionFactory
import org.keycloak.provider.ProviderConfigProperty

@Slf4j
class ThirdPartyAuthenticatorFactory : AuthenticatorFactory {

    companion object {
        private const val PROVIDER_ID = "third-party-authenticator"
        private val AUTHENTICATOR = ThirdPartyAuthenticator
    }

    override fun create(session: KeycloakSession): Authenticator = AUTHENTICATOR

    override fun init(configScope: Config.Scope) = Unit

    override fun postInit(factory: KeycloakSessionFactory) = Unit

    override fun close() = Unit

    override fun getId(): String = PROVIDER_ID

    override fun getHelpText(): String = "Authenticate by third party system"

    override fun getConfigProperties(): MutableList<ProviderConfigProperty> = mutableListOf(
        ProviderConfigProperty(
            "third-party-api-base-url",
            "API Base URL",
            "The API endpoint of the third-party-auth provider",
            ProviderConfigProperty.STRING_TYPE,
            "",
            false
        ),
        ProviderConfigProperty(
            "third-party-api-client-id",
            "API Client ID",
            "Client ID",
            ProviderConfigProperty.STRING_TYPE,
            "",
            false
        ),
        ProviderConfigProperty(
            "third-party-api-client-secret",
            "API Client Secret",
            "Client Secret",
            ProviderConfigProperty.STRING_TYPE,
            "",
            true
        )
    )

    override fun getDisplayType(): String = "Third Party Authenticator"

    override fun getReferenceCategory(): String = "third-party"

    override fun isConfigurable(): Boolean = true

    override fun getRequirementChoices(): Array<AuthenticationExecutionModel.Requirement> = REQUIREMENT_CHOICES

    override fun isUserSetupAllowed(): Boolean = false
}