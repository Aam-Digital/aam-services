package com.aamdigital.aamintegration.authentication.core

import com.aamdigital.aamintegration.error.AamErrorCode
import com.aamdigital.aamintegration.error.InternalServerException
import org.keycloak.admin.client.CreatedResponseUtil
import org.keycloak.admin.client.Keycloak
import org.keycloak.representations.idm.UserRepresentation
import java.util.*


class AamKeycloakAuthenticationProvider(
    private val keycloak: Keycloak
) : AuthenticationProvider {

    enum class AamKeycloakAuthenticationProviderError : AamErrorCode {
        USER_CREATION_ERROR,
    }

    override fun createExternalUser(
        realmId: String,
        firstName: String,
        lastName: String,
        email: String,
        externalUserId: String,
    ): UserModel {
        val newUser = UserRepresentation()

        newUser.username = "external_$externalUserId"
        newUser.firstName = firstName
        newUser.lastName = lastName
        newUser.email = email
        newUser.isEnabled = true
        newUser.isEmailVerified = true

        val userResource = keycloak.realm(realmId).users()
        val response = userResource.create(newUser)

        if (response.status != 201) {
            throw InternalServerException(
                code = AamKeycloakAuthenticationProviderError.USER_CREATION_ERROR,
                message = "Could not create externalUser"
            )
        }

        val clientId = CreatedResponseUtil.getCreatedId(response)

        return keycloak.realm(realmId).users().get(clientId).toRepresentation().let {
            UserModel(
                userId = it.id,
                userName = it.username,
                firstName = it.firstName,
                lastName = it.lastName,
                email = it.email,
            )
        }
    }

    override fun findByExternalId(
        realmId: String,
        externalUserId: String,
    ): Optional<UserModel> {
        val users = keycloak.realm(realmId).users().search("external_$externalUserId")

        if (users.isEmpty()) {
            return Optional.empty()
        }

        return users.first().let {
            Optional.of(
                UserModel(
                    userId = it.id,
                    userName = it.username,
                    firstName = it.firstName,
                    lastName = it.lastName,
                    email = it.email,
                )
            )
        }
    }
}
