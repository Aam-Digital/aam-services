package com.aamdigital.aamintegration.authentication.core

import com.aamdigital.aamintegration.error.AamErrorCode
import com.aamdigital.aamintegration.error.InternalServerException
import org.keycloak.admin.client.CreatedResponseUtil
import org.keycloak.admin.client.Keycloak
import org.keycloak.representations.idm.UserRepresentation
import org.slf4j.LoggerFactory
import java.util.*


class AamKeycloakAuthenticationProvider(
    private val keycloak: Keycloak
) : AuthenticationProvider {

    private val logger = LoggerFactory.getLogger(javaClass)

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

        val response = try {
            keycloak.realm(realmId).users().create(newUser)
        } catch (ex: Exception) {
            logger.warn("KeycloakError: {}, {}", realmId, newUser, ex)
            throw InternalServerException(
                code = AamKeycloakAuthenticationProviderError.USER_CREATION_ERROR,
                message = "Could not create externalUser"
            )
        }

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

    override fun findByEmail(
        realmId: String,
        email: String,
    ): Optional<UserModel> {
        val users = try {
            keycloak.realm(realmId).users().searchByEmail(email, true)
        } catch (ex: Exception) {
            logger.warn("KeycloakError: {}, {}", realmId, email)
            logger.warn(ex.localizedMessage, ex)
            return Optional.empty()
        }

        if (users.isEmpty()) {
            logger.debug("No user found for with email {}", email)
            return Optional.empty()
        }

        logger.debug("Users found: {}", users.size)

        return users.first().let {
            logger.debug("User found {}", it)
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
