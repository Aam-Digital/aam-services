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
                "third-party-api-base-url",
                "API Base URL",
                "The API endpoint of the third-party-auth provider",
                ProviderConfigProperty.STRING_TYPE,
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
