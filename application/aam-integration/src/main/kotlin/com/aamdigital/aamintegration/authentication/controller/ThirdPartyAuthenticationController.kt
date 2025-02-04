package com.aamdigital.aamintegration.authentication.controller

import com.aamdigital.aamintegration.authentication.core.CreateSessionUseCase
import com.aamdigital.aamintegration.authentication.core.CreateSessionUseCaseRequest
import com.aamdigital.aamintegration.authentication.core.VerifySessionUseCase
import com.aamdigital.aamintegration.authentication.core.VerifySessionUseCaseRequest
import com.aamdigital.aamintegration.authentication.di.AamKeycloakConfig
import com.aamdigital.aamintegration.domain.UseCaseOutcome
import com.aamdigital.aamintegration.error.HttpErrorDto
import jakarta.validation.constraints.Email
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

data class UserSessionRequest(
    val realmId: String,
    val userId: String,
    val firstName: String,
    val lastName: String,
    @Email
    val email: String,
    val additionalData: Map<String, String> = emptyMap()
)

data class UserSessionDto(
    val sessionId: String,
    val sessionToken: String,
    val entryPointUrl: String,
    val validUntil: String,
)

data class UserSessionDataDto(
    val userId: String,
)

@RestController
@RequestMapping("/v1/authentication")
@ConditionalOnProperty(
    prefix = "features.third-party-authentication",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = false
)
@Validated
class ThirdPartyAuthenticationController(
    private val createSessionUseCase: CreateSessionUseCase,
    private val verifySessionUseCase: VerifySessionUseCase,
    private val aamKeycloakConfig: AamKeycloakConfig,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @PostMapping("/session")
    @PreAuthorize("hasAuthority('ROLE_third-party-authentication-provider')")
    fun startSession(
        @RequestBody userSessionRequest: UserSessionRequest
    ): ResponseEntity<Any> {

        // todo check realm permissions

        val response = createSessionUseCase.run(
            CreateSessionUseCaseRequest(
                realmId = userSessionRequest.realmId,
                userId = userSessionRequest.userId,
                firstName = userSessionRequest.firstName,
                lastName = userSessionRequest.lastName,
                email = userSessionRequest.email,
                additionalData = userSessionRequest.additionalData
            )
        )

        return when (response) {
            is UseCaseOutcome.Success -> {
                val entryPointUrl =
                    "https://${userSessionRequest.realmId}.aam-digital.net/login" +
                            "?tpa_session=${response.data.sessionId}:${response.data.sessionToken}"

                ResponseEntity.ok(
                    UserSessionDto(
                        sessionId = response.data.sessionId,
                        sessionToken = response.data.sessionToken,
                        entryPointUrl = entryPointUrl,
                        validUntil = response.data.validUntil.toString()
                    )
                )
            }

            is UseCaseOutcome.Failure -> {
                logger.warn(response.errorMessage, response.errorCode, response.cause)
                ResponseEntity.badRequest().body(
                    HttpErrorDto(
                        errorMessage = response.errorMessage,
                        errorCode = response.errorCode.toString()
                    )
                )
            }
        }
    }

    @GetMapping("/session/{sessionId}")
    @PreAuthorize("permitAll()")
    fun getSession(
        @PathVariable sessionId: String,
        @RequestParam("session_token", required = true) sessionToken: String,
    ): ResponseEntity<Any> {

        val response = verifySessionUseCase.run(
            VerifySessionUseCaseRequest(
                sessionId = sessionId,
                sessionToken = sessionToken
            )
        )

        return when (response) {
            is UseCaseOutcome.Success -> {
                ResponseEntity.ok(
                    UserSessionDataDto(
                        userId = response.data.userId,
                    )
                )
            }

            is UseCaseOutcome.Failure -> {
                ResponseEntity.badRequest().body(
                    HttpErrorDto(
                        errorMessage = response.errorMessage,
                        errorCode = response.errorCode.toString()
                    )
                )
            }
        }
    }

}
