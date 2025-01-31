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

    private static final String QUERY_PARAM_SESSION_ID = "tpa_session_id";
    private static final String QUERY_PARAM_SESSION_TOKEN = "tpa_session_token";

    private static final TypeReference<HashMap<String, String>> typeRef = new TypeReference<>() {
    };

    private String getSessionUser(AuthenticationFlowContext ctx, String sessionId, String sessionToken) {
        HttpClient httpClient = ctx.getSession().getProvider(HttpClientProvider.class).getHttpClient();

        var httpGet = new HttpGet(
                String.format(
                        "https://api.aam-digital.dev/v1/authentication/session/%s?session_token=%s",
                        sessionId,
                        sessionToken
                )
        );

        try {
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
        var sessionId = ctx.getHttpRequest().getUri().getQueryParameters().getFirst(QUERY_PARAM_SESSION_ID);
        var sessionToken = ctx.getHttpRequest().getUri().getQueryParameters().getFirst(QUERY_PARAM_SESSION_TOKEN);

        if (sessionId == null || sessionToken == null) {
            ctx.attempted();
            return;
        }

        var userId = getSessionUser(ctx, sessionId, sessionToken);

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
