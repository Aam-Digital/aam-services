package com.aamdigital.aambackendservice.thirdpartyauthentication.controller

import com.aamdigital.aambackendservice.common.domain.ApplicationConfig
import com.aamdigital.aambackendservice.common.domain.UseCaseOutcome
import com.aamdigital.aambackendservice.common.error.HttpErrorDto
import com.aamdigital.aambackendservice.thirdpartyauthentication.CreateSessionUseCase
import com.aamdigital.aambackendservice.thirdpartyauthentication.CreateSessionUseCaseRequest
import com.aamdigital.aambackendservice.thirdpartyauthentication.SessionRedirectUseCase
import com.aamdigital.aambackendservice.thirdpartyauthentication.SessionRedirectUseCaseRequest
import com.aamdigital.aambackendservice.thirdpartyauthentication.VerifySessionUseCase
import com.aamdigital.aambackendservice.thirdpartyauthentication.VerifySessionUseCaseRequest
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
import java.security.Principal

data class UserSessionRequest(
    val userId: String,
    val firstName: String,
    val lastName: String,
    val redirectUrl: String? = null,
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

data class UserSessionRedirectDto(
    val redirectUrl: String,
)

@ConditionalOnProperty(
    prefix = "features.third-party-authentication",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = false
)
@RestController
@RequestMapping("/v1/third-party-authentication")
@Validated
class ThirdPartyAuthenticationController(
    private val createSessionUseCase: CreateSessionUseCase,
    private val verifySessionUseCase: VerifySessionUseCase,
    private val sessionRedirectUseCase: SessionRedirectUseCase,
    private val applicationConfig: ApplicationConfig,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @PostMapping("/session")
    @PreAuthorize("hasAuthority('ROLE_third-party-authentication-provider')")
    fun startSession(
        @RequestBody userSessionRequest: UserSessionRequest
    ): ResponseEntity<Any> {
        val response = createSessionUseCase.run(
            CreateSessionUseCaseRequest(
                userId = userSessionRequest.userId,
                firstName = userSessionRequest.firstName,
                lastName = userSessionRequest.lastName,
                redirectUrl = userSessionRequest.redirectUrl,
                email = userSessionRequest.email,
                additionalData = userSessionRequest.additionalData
            )
        )

        return when (response) {
            is UseCaseOutcome.Success -> {
                // add https:// if not already part of applicationConfig.baseUrl
                val baseUrl = if (applicationConfig.baseUrl.startsWith("http://") || applicationConfig.baseUrl.startsWith("https://")) {
                    applicationConfig.baseUrl
                } else {
                    "https://${applicationConfig.baseUrl}"
                }
                val entryPointUrl =
                    "${baseUrl}/login?tpa_session=${response.data.sessionId}:${response.data.sessionToken}"

                logger.trace("[POST /session]: Created session for ${userSessionRequest.email}")
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
                sessionToken = sessionToken,
            )
        )
        return when (response) {
            is UseCaseOutcome.Success -> {
                logger.trace("[GET /session/{sessionId}]: Validated session ${sessionId} for ${response.data.userId}")
                ResponseEntity.ok(
                    UserSessionDataDto(
                        userId = response.data.userId,
                    )
                )
            }

            is UseCaseOutcome.Failure -> {
                logger.warn("[GET /session/{sessionId}]: Failed to validate session ${sessionId}: ${response.errorMessage}")
                ResponseEntity.badRequest().body(
                    HttpErrorDto(
                        errorMessage = response.errorMessage,
                        errorCode = response.errorCode.toString()
                    )
                )
            }
        }
    }

    @GetMapping("/session/{sessionId}/redirect")
    fun getSessionRedirect(
        @PathVariable sessionId: String,
        principal: Principal,
    ): ResponseEntity<Any> {
        val response = sessionRedirectUseCase.run(
            SessionRedirectUseCaseRequest(
                sessionId = sessionId,
                userId = principal.name
            )
        )

        return when (response) {
            is UseCaseOutcome.Success -> {
                ResponseEntity.ok(
                    UserSessionRedirectDto(
                        redirectUrl = response.data.redirectUrl,
                    )
                )
            }

            is UseCaseOutcome.Failure -> {
                logger.warn("[GET /session/{sessionId}/redirect]: Failed to return redirect for session ${sessionId}: ${response.errorMessage}")
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
