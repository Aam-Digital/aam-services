package com.aamdigital.aamintegration.authentication.core

import java.util.*

data class UserModel(
    val userId: String,
    val userName: String,
    val firstName: String,
    val lastName: String,
    val email: String,
)

interface AuthenticationProvider {
    fun createExternalUser(
        realmId: String,
        firstName: String,
        lastName: String,
        email: String,
        externalUserId: String,
    ): UserModel

    fun findByExternalId(
        realmId: String,
        externalUserId: String
    ): Optional<UserModel>
}
