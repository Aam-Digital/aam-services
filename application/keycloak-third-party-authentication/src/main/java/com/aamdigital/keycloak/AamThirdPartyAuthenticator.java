package com.aamdigital.keycloak;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.util.EntityUtils;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.Authenticator;
import org.keycloak.connections.httpclient.HttpClientProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

import java.io.IOException;
import java.util.HashMap;

public class AamThirdPartyAuthenticator implements Authenticator {
    private static final String QUERY_PARAM_IDP_HINT = "kc_idp_hint";

    private static final TypeReference<HashMap<String, String>> typeRef = new TypeReference<>() {
    };

    private static HttpGet checkCredentialsRequest(String apiBaseUrl, String tpaSession) {
        var sessionCredentials = tpaSession.split(":");
        String clientId = null;
        String clientToken = null;

        if (sessionCredentials.length == 3 && sessionCredentials[0].equals("tpa_session")) {
            clientId = sessionCredentials[1];
            clientToken = sessionCredentials[2];
        }

        return new HttpGet(
                String.format(
                        "%s/v1/authentication/session/%s?session_token=%s",
                        apiBaseUrl,
                        clientId,
                        clientToken
                )
        );
    }

    private String getSessionUser(AuthenticationFlowContext ctx, String tpaSession) {
        try {
            var apiBaseUrl = ctx.getAuthenticatorConfig().getConfig().get("third-party-api-base-url");

            if (apiBaseUrl == null) {
                return null;
            }

            var httpGet = checkCredentialsRequest(apiBaseUrl, tpaSession);

            HttpClient httpClient = ctx.getSession().getProvider(HttpClientProvider.class).getHttpClient();
            var response = httpClient.execute(httpGet);

            var entity = response.getEntity();
            if (entity == null) {
                return null;
            }

            var mapper = new ObjectMapper();
            var mappedResponse = mapper.readValue(EntityUtils.toString(entity), typeRef);

            return mappedResponse.get("userId");
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public void authenticate(AuthenticationFlowContext ctx) {
        var tpaSession = ctx.getHttpRequest().getUri().getQueryParameters().getFirst(QUERY_PARAM_IDP_HINT);

        if (tpaSession == null) {
            ctx.attempted();
            return;
        }

        var userId = getSessionUser(ctx, tpaSession);

        if (userId == null) {
            ctx.failure(AuthenticationFlowError.INVALID_CREDENTIALS);
            return;
        }

        var sessionUser = ctx.getSession().users()
                .getUserById(ctx.getRealm(),
                        userId
                );

        if (sessionUser == null) {
            ctx.failure(AuthenticationFlowError.UNKNOWN_USER);
            return;
        }

        ctx.setUser(sessionUser);

        ctx.success();
    }

    @Override
    public void action(AuthenticationFlowContext authenticationFlowContext) {
    }

    @Override
    public boolean requiresUser() {
        return false;
    }

    @Override
    public boolean configuredFor(KeycloakSession keycloakSession, RealmModel realmModel, UserModel userModel) {
        return true;
    }

    @Override
    public void setRequiredActions(KeycloakSession keycloakSession, RealmModel realmModel, UserModel userModel) {
    }

    @Override
    public void close() {
    }
}
