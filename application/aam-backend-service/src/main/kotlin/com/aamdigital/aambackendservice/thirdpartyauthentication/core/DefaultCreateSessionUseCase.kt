package com.aamdigital.aambackendservice.thirdpartyauthentication.core

import com.aamdigital.aambackendservice.common.couchdb.core.CouchDbClient
import com.aamdigital.aambackendservice.common.domain.UseCaseOutcome
import com.aamdigital.aambackendservice.common.error.AamException
import com.aamdigital.aambackendservice.thirdpartyauthentication.CreateSessionUseCase
import com.aamdigital.aambackendservice.thirdpartyauthentication.CreateSessionUseCaseData
import com.aamdigital.aambackendservice.thirdpartyauthentication.CreateSessionUseCaseRequest
import com.aamdigital.aambackendservice.thirdpartyauthentication.repository.ThirdPartyAuthSession
import com.aamdigital.aambackendservice.thirdpartyauthentication.repository.ThirdPartyAuthSessionRepository
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.util.*

class DefaultCreateSessionUseCase(
    private val authenticationSessionStore: AuthenticationSessionStore,
    private val thirdPartyAuthSessionRepository: ThirdPartyAuthSessionRepository,
    private val passwordEncoder: PasswordEncoder,
    private val authenticationProvider: AuthenticationProvider,
    private val couchDbClient: CouchDbClient,
    private val sessionValidity: Duration
) : CreateSessionUseCase() {

    override fun apply(request: CreateSessionUseCaseRequest): UseCaseOutcome<CreateSessionUseCaseData> {
        val user: UserModel =
            try {
                findOrCreateUser(request)
            } catch (ex: AamException) {
                return UseCaseOutcome.Failure(
                    errorMessage = ex.localizedMessage,
                    errorCode = ex.code,
                    cause = ex
                )
            }

        val sessionToken = UUID.randomUUID().toString().replace("-", "")
        val sessionId = UUID.randomUUID().toString()
        val validUntil = OffsetDateTime.now().plus(sessionValidity)

        val redirectUrl =
            if (request.redirectUrl.isNullOrBlank()) {
                // todo: tola specific, needs update on tola site to use the redirectUrl parameter directly
                request.additionalData["tola_frontend_url"]
            } else {
                request.redirectUrl
            }

        authenticationSessionStore.store(
            AuthenticationSession(
                sessionId = sessionId,
                userId = user.userId,
                externalUserId = request.userId,
                sessionTokenHash = passwordEncoder.encode(sessionToken),
                validUntil = validUntil
            )
        )

        // stored even when there is no redirect url, so that a later redirect lookup can answer
        // "no redirect for this session" instead of "unknown session"
        thirdPartyAuthSessionRepository.save(
            ThirdPartyAuthSession(
                sessionId = sessionId,
                userId = user.userId,
                redirectUrl = redirectUrl,
                createdAt = Instant.now()
            )
        )

        return UseCaseOutcome.Success(
            CreateSessionUseCaseData(
                sessionId = sessionId,
                sessionToken = sessionToken,
                entryPointUrl = "",
                validUntil = validUntil
            )
        )
    }

    private fun findOrCreateUser(request: CreateSessionUseCaseRequest): UserModel {
        val existingUser = authenticationProvider.findByEmail(request.email)

        return if (existingUser.isEmpty) {
            // generate uuid (could be passed via API request in the future)
            val userEntityId = "User:" + UUID.randomUUID().toString()

            val newAccount =
                authenticationProvider.createExternalUser(
                    firstName = request.firstName,
                    lastName = request.lastName,
                    email = request.email,
                    externalUserId = request.userId,
                    userEntityId = userEntityId
                )
            logger.info("Created new Keycloak User {} for {}", newAccount.userId, newAccount.email)

            createUserEntity(userEntityId, request.firstName, request.lastName)
            logger.info("Created new CouchDB User {}", userEntityId)

            return newAccount
        } else {
            existingUser.get()
        }
    }

    private fun createUserEntity(
        entityId: String,
        firstName: String,
        lastName: String
    ) {
        val entity =
            UserEntity(
                id = entityId,
                name = "$firstName $lastName"
            )

        couchDbClient
            .putDatabaseDocument(
                database = "app",
                documentId = entity.id,
                body = entity
            )
    }
}
