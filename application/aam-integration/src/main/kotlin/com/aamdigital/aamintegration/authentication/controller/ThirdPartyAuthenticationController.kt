package com.aamdigital.aamintegration.authentication.controller

import jakarta.validation.constraints.Email
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.time.ZoneOffset
import java.util.*

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

@RestController
@RequestMapping("/v1/authentication")
@ConditionalOnProperty(
    prefix = "features.third-party-authentication",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = false
)
@Validated
class ThirdPartyAuthenticationController {

    private val logger = LoggerFactory.getLogger(javaClass)

    @PostMapping("/session")
    @PreAuthorize("hasAuthority('ROLE_third-party-authentication-provider')")
    fun startSession(
        @RequestBody userSessionRequest: UserSessionRequest
    ): ResponseEntity<Any> {

        logger.info("startSession: $userSessionRequest")

        return ResponseEntity.ok(
            UserSessionDto(
                sessionId = UUID.randomUUID().toString(),
                sessionToken = UUID.randomUUID().toString().replace("-", ""),
                entryPointUrl = "https://api.aam-digital.dev/v1/auth/authentication/entry-point",
                validUntil = Instant.now().plusSeconds(120).atOffset(ZoneOffset.UTC).toString()
            )
        )
    }

}
