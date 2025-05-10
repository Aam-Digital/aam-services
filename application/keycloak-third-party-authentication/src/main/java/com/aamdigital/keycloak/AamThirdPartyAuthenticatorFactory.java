package com.aamdigital.keycloak;


import org.keycloak.Config;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.AuthenticatorFactory;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;

import java.util.LinkedList;
import java.util.List;

public class AamThirdPartyAuthenticatorFactory implements AuthenticatorFactory {

    public static final String PROVIDER_ID = "third-party-authenticator";
    private static final Authenticator SINGLETON = new AamThirdPartyAuthenticator();

    public static final String THIRD_PARTY_API_BASE_URL = "third-party-api-base-url";
    public static final String THIRD_PARTY_EXTERNAL_LOGIN_URL = "third-party-external-login-url";
    public static final String ONLY_EXTERNAL_LOGIN = "only-external-login";

    @Override
    public Authenticator create(KeycloakSession session) {
        return SINGLETON;
    }

    @Override
    public String getDisplayType() {
        return "Aam Digital - Third Party Authenticator";
    }

    @Override
    public String getReferenceCategory() {
        return "third-party";
    }

    @Override
    public boolean isConfigurable() {
        return true;
    }

    @Override
    public AuthenticationExecutionModel.Requirement[] getRequirementChoices() {
        return REQUIREMENT_CHOICES;
    }

    @Override
    public boolean isUserSetupAllowed() {
        return false;
    }

    @Override
    public String getHelpText() {
        return "Authenticate by third party system";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        var list = new LinkedList<ProviderConfigProperty>();

        list.add(new ProviderConfigProperty(
                AamThirdPartyAuthenticatorFactory.THIRD_PARTY_API_BASE_URL,
                "API Base URL",
                "The API endpoint of the third-party-auth provider",
                ProviderConfigProperty.STRING_TYPE,
                "",
                false
        ));

        list.add(new ProviderConfigProperty(
                AamThirdPartyAuthenticatorFactory.THIRD_PARTY_EXTERNAL_LOGIN_URL,
                "External System Login Page",
                "The URL to the external system's login page, to which the user will be redirected if no valid token is passed.",
                ProviderConfigProperty.STRING_TYPE,
                "",
                false
        ));

        list.add(new ProviderConfigProperty(
                AamThirdPartyAuthenticatorFactory.ONLY_EXTERNAL_LOGIN,
                "Force only external login",
                "Check this if users should always be redirected to the external system's login page and cannot enter username and password directly.",
                ProviderConfigProperty.BOOLEAN_TYPE,
                "",
                false
        ));

        return list;
    }

    @Override
    public void init(Config.Scope config) {
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
    }

    @Override
    public void close() {
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }
}
