package com.aamdigital.aambackendservice.thirdpartyauthentication.core

import java.util.*

data class UserModel(
    val userId: String,
    val userName: String,
    val firstName: String,
    val lastName: String,
    val email: String,
)

interface AuthenticationProvider {
    /**
     * Create a new user in the authentication system (e.g. Keycloak).
     * @param userEntityId (optional) the user entity id in the CouchDB that should be linked to the new user
     */
    fun createExternalUser(
        realmId: String,
        firstName: String,
        lastName: String,
        email: String,
        externalUserId: String,
        userEntityId: String? = null,
    ): UserModel

    fun findByEmail(
        realmId: String,
        email: String
    ): Optional<UserModel>
}
