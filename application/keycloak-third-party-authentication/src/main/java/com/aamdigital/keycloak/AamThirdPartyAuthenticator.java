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
import java.util.Map;
import java.util.logging.Logger;

public class AamThirdPartyAuthenticator implements Authenticator {
    private static final Logger log = Logger.getLogger(AamThirdPartyAuthenticator.class.getName());

    private static final String QUERY_PARAM_IDP_HINT = "kc_idp_hint";

    /**
     * Prefix of the login hint that carries a third-party session ("tpa_session:sessionId:sessionToken").
     * Any other hint (e.g. the alias of an identity provider) is left to the following steps of the login flow,
     * unless the authenticator is configured for "only external login", which rejects every other login.
     */
    private static final String TPA_SESSION_HINT_PREFIX = "tpa_session:";

    private static final TypeReference<HashMap<String, String>> typeRef = new TypeReference<>() {
    };

    /**
     * The settings of the step. Empty if the step has not been configured (yet), so that it then does not sign in
     * anyone instead of failing every login of the realm.
     */
    private static Map<String, String> configOf(AuthenticationFlowContext ctx) {
        var config = ctx.getAuthenticatorConfig();
        if (config == null || config.getConfig() == null) {
            return Map.of();
        }
        return config.getConfig();
    }

    /**
     * The session id of a "tpa_session:sessionId:sessionToken" hint, for logging without the secret session token.
     */
    private static String sessionIdOf(String tpaSession) {
        var sessionCredentials = tpaSession.split(":");
        return sessionCredentials.length == 3 ? sessionCredentials[1] : "(invalid)";
    }

    private static HttpGet checkCredentialsRequest(String apiBaseUrl, String tpaSession) {
        var sessionCredentials = tpaSession.split(":");
        String clientId = null;
        String clientToken = null;

        if (sessionCredentials.length == 3 && sessionCredentials[0].equals("tpa_session")) {
            clientId = sessionCredentials[1];
            clientToken = sessionCredentials[2];
        }

        // ensure no trailing slash
        if (apiBaseUrl.endsWith("/")) {
            apiBaseUrl = apiBaseUrl.substring(0, apiBaseUrl.length() - 1);
        }

        return new HttpGet(
                String.format(
                        "%s/v1/third-party-authentication/session/%s?session_token=%s",
                        apiBaseUrl,
                        clientId,
                        clientToken
                )
        );
    }

    @Override
    public void authenticate(AuthenticationFlowContext ctx) {
        var tpaSession = ctx.getHttpRequest().getUri().getQueryParameters().getFirst(QUERY_PARAM_IDP_HINT);

        if (tpaSession == null || !tpaSession.startsWith(TPA_SESSION_HINT_PREFIX)) {
            var forceExternalLogin = configOf(ctx).get(AamThirdPartyAuthenticatorFactory.ONLY_EXTERNAL_LOGIN);
            if (forceExternalLogin != null && forceExternalLogin.equals("true")) {
                // no session found but forcing external login -> redirect
                failAuthentication(ctx, AuthenticationFlowError.CREDENTIAL_SETUP_REQUIRED);
                return;
            } else {
                ctx.attempted();
                return;
            }
        }

        log.info("[Aam SSO] authenticate with session: " + sessionIdOf(tpaSession));

        var userId = getSessionUser(ctx, tpaSession);
        log.info("[Aam SSO] validated userId: " + userId);

        if (userId == null) {
            log.info("[Aam SSO] no valid session found");
            failAuthentication(ctx, AuthenticationFlowError.INVALID_CREDENTIALS);
            return;
        }

        var sessionUser = ctx.getSession().users()
                .getUserById(ctx.getRealm(),
                        userId
                );

        if (sessionUser == null) {
            log.info("[Aam SSO] unknown user " + userId);
            failAuthentication(ctx, AuthenticationFlowError.UNKNOWN_USER);
            return;
        }

        ctx.setUser(sessionUser);
        log.fine("[Aam SSO] set user");

        ctx.success();
    }

    private void failAuthentication(AuthenticationFlowContext ctx, AuthenticationFlowError error) {
        var externalLoginUrl = configOf(ctx).get(AamThirdPartyAuthenticatorFactory.THIRD_PARTY_EXTERNAL_LOGIN_URL);

        ctx.failureChallenge(
            error,
            ctx.form()
                .setError(error.toString())
                .setAttribute("externalLoginUrl", externalLoginUrl)
                .createForm("aam-third-party-login-result.ftl")
        );
    }

    private String getSessionUser(AuthenticationFlowContext ctx, String tpaSession) {
        try {
            var apiBaseUrl = configOf(ctx).get(AamThirdPartyAuthenticatorFactory.THIRD_PARTY_API_BASE_URL);

            if (apiBaseUrl == null) {
                log.warning("[Aam SSO] the authenticator has no API base URL configured");
                return null;
            }

            var httpGet = checkCredentialsRequest(apiBaseUrl, tpaSession);
            // not the request itself: its URL contains the secret session token
            log.info("[Aam SSO] checking session " + sessionIdOf(tpaSession) + " at " + apiBaseUrl);

            HttpClient httpClient = ctx.getSession().getProvider(HttpClientProvider.class).getHttpClient();
            var response = httpClient.execute(httpGet);

            var entity = response.getEntity();
            log.info("[Aam SSO] response checking session: " + entity);
            if (entity == null) {
                return null;
            }

            var mapper = new ObjectMapper();
            var mappedResponse = mapper.readValue(EntityUtils.toString(entity), typeRef);

            return mappedResponse.get("userId");
        } catch (IOException e) {
            log.warning("[Aam SSO] error checking session with API: " + e.getMessage());
            return null;
        }
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
