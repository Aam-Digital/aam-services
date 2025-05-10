package com.aamdigital.aambackendservice.thirdpartyauthentication.core

import com.aamdigital.aambackendservice.common.error.AamErrorCode
import com.aamdigital.aambackendservice.common.error.InternalServerException
import com.aamdigital.aambackendservice.common.error.InvalidArgumentException
import com.aamdigital.aambackendservice.thirdpartyauthentication.di.AamKeycloakConfig
import org.keycloak.admin.client.CreatedResponseUtil
import org.keycloak.admin.client.Keycloak
import org.keycloak.representations.idm.UserRepresentation
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.util.*


class AamKeycloakAuthenticationProvider(
    private val keycloak: Keycloak,
    private val keycloakConfig: AamKeycloakConfig,
) : AuthenticationProvider {

    private val logger = LoggerFactory.getLogger(javaClass)

    enum class AamKeycloakAuthenticationProviderError : AamErrorCode {
        USER_CREATION_ERROR,
    }

    override fun createExternalUser(
        firstName: String,
        lastName: String,
        email: String,
        externalUserId: String,
        userEntityId: String?,
    ): UserModel {
        val newUser = UserRepresentation()

        newUser.username = "external_$externalUserId"
        newUser.firstName = firstName
        newUser.lastName = lastName
        newUser.email = email
        newUser.isEnabled = true
        newUser.isEmailVerified = true
        if(userEntityId != null) {
            newUser.singleAttribute("exact_username", userEntityId)
        }

        val response = try {
            keycloak.realm(keycloakConfig.realm).users().create(newUser)
        } catch (ex: Exception) {
            logger.warn("KeycloakError: {}", newUser, ex)
            throw InternalServerException(
                code = AamKeycloakAuthenticationProviderError.USER_CREATION_ERROR,
                message = "Could not create externalUser"
            )
        }

        if (response.status != 201) {
            val responseLines = (response.entity as InputStream).bufferedReader().readLines()
            logger.debug("Response: {}", responseLines.toString())
            if (response.status == 409) {
                throw InvalidArgumentException(
                    code = AamKeycloakAuthenticationProviderError.USER_CREATION_ERROR,
                    message = "User with username ${newUser.username} already exists for a different email ID"
                )
            }

            throw InternalServerException(
                code = AamKeycloakAuthenticationProviderError.USER_CREATION_ERROR,
                message = "Could not create externalUser"
            )
        }

        val clientId = CreatedResponseUtil.getCreatedId(response)

        return keycloak.realm(keycloakConfig.realm).users().get(clientId).toRepresentation().let {
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
        email: String,
    ): Optional<UserModel> {
        val users = try {
            keycloak.realm(keycloakConfig.realm).users().searchByEmail(email, true)
        } catch (ex: Exception) {
            logger.warn("KeycloakError: {}", email)
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
                    firstName = it.firstName ?: "",
                    lastName = it.lastName ?: "",
                    email = it.email,
                )
            )
        }
    }
}
