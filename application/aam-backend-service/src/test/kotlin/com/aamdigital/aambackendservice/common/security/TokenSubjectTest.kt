package com.aamdigital.aambackendservice.common.security

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import java.security.Principal

class TokenSubjectTest {
    private fun authentication(configure: Jwt.Builder.() -> Unit): JwtAuthenticationToken =
        JwtAuthenticationToken(
            Jwt
                .withTokenValue("token")
                .header("alg", "none")
                .claim("azp", "app")
                .apply(configure)
                .build()
        )

    @Test
    fun `should return the sub claim of the access token`() {
        // Given
        val authentication = authentication { subject("user-1") }

        // When
        val subject = authentication.tokenSubject

        // Then
        assertThat(subject).isEqualTo("user-1")
    }

    @Test
    fun `should return null for an access token without sub claim`() {
        // Given
        val authentication = authentication { claim("username", "user-1") }

        // When
        val subject = authentication.tokenSubject

        // Then
        assertThat(subject).isNull()
    }

    @Test
    fun `should return the name of a principal that is not a JWT authentication`() {
        // Given
        val principal = Principal { "user-1" }

        // When
        val subject = principal.tokenSubject

        // Then
        assertThat(subject).isEqualTo("user-1")
    }
}
