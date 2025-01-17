package com.aamdigital.keycloak

import lombok.extern.slf4j.Slf4j
import org.keycloak.authentication.AuthenticationFlowContext
import org.keycloak.authentication.Authenticator
import org.keycloak.models.KeycloakSession
import org.keycloak.models.RealmModel
import org.keycloak.models.UserModel

@Slf4j
object ThirdPartyAuthenticator : Authenticator {

    override fun close() {
        TODO("Not yet implemented")
    }

    override fun authenticate(context: AuthenticationFlowContext) {
        TODO("Not yet implemented")
    }

    override fun action(context: AuthenticationFlowContext) {
        TODO("Not yet implemented")
    }

    override fun requiresUser(): Boolean {
        TODO("Not yet implemented")
    }

    override fun configuredFor(session: KeycloakSession, realm: RealmModel, user: UserModel): Boolean {
        TODO("Not yet implemented")
    }

    override fun setRequiredActions(session: KeycloakSession, realm: RealmModel, user: UserModel) {
        TODO("Not yet implemented")
    }
}