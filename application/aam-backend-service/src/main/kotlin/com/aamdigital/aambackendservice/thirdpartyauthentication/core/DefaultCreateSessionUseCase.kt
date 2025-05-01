package com.aamdigital.aambackendservice.thirdpartyauthentication.core

import com.aamdigital.aambackendservice.domain.UseCaseOutcome
import com.aamdigital.aambackendservice.error.AamException
import com.aamdigital.aambackendservice.thirdpartyauthentication.CreateSessionUseCase
import com.aamdigital.aambackendservice.thirdpartyauthentication.CreateSessionUseCaseData
import com.aamdigital.aambackendservice.thirdpartyauthentication.CreateSessionUseCaseRequest
import com.aamdigital.aambackendservice.thirdpartyauthentication.repository.AuthenticationSessionEntity
import com.aamdigital.aambackendservice.thirdpartyauthentication.repository.AuthenticationSessionRepository
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.OffsetDateTime
import java.util.*

class DefaultCreateSessionUseCase(
    private val authenticationSessionRepository: AuthenticationSessionRepository,
    private val passwordEncoder: PasswordEncoder,
    private val authenticationProvider: AuthenticationProvider,
) : CreateSessionUseCase() {

    companion object {
        private const val SESSION_VALID_IN_MINUTES = 5L
    }

    override fun apply(request: CreateSessionUseCaseRequest): UseCaseOutcome<CreateSessionUseCaseData> {
        val user: UserModel = try {
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
        val validUntil = OffsetDateTime.now().plusMinutes(SESSION_VALID_IN_MINUTES)

        val redirectUrl = if (request.redirectUrl.isNullOrBlank()) {
            // todo: tola specific, needs update on tola site to use the redirectUrl parameter directly
            request.additionalData["tola_frontend_url"]
        } else {
            request.redirectUrl
        }

        val session = AuthenticationSessionEntity(
            externalIdentifier = sessionId,
            sessionToken = passwordEncoder.encode(sessionToken),
            externalUserId = request.userId,
            userId = user.userId,
            redirectUrl = redirectUrl,
            validUntil = validUntil,
        )

        authenticationSessionRepository.save(session)

        return UseCaseOutcome.Success(
            CreateSessionUseCaseData(
                sessionId = sessionId,
                sessionToken = sessionToken,
                entryPointUrl = "",
                validUntil = validUntil,
            )
        )
    }

    private fun findOrCreateUser(request: CreateSessionUseCaseRequest): UserModel {
        val existingUser = authenticationProvider.findByEmail(
            realmId = request.realmId,
            email = request.email,
        )

        return if (existingUser.isEmpty) {
            authenticationProvider.createExternalUser(
                realmId = request.realmId,
                firstName = request.firstName,
                lastName = request.lastName,
                email = request.email,
                externalUserId = request.userId,
            )
        } else {
            existingUser.get()
        }
    }
}
