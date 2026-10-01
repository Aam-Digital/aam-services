package com.aamdigital.aambackendservice.common.security

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.jwt.Jwt

class AamAuthenticationConverterTest {
    private val converter = AamAuthenticationConverter(frontendClientId = "app")

    private fun jwt(vararg claims: Pair<String, Any>): Jwt =
        Jwt
            .withTokenValue("token")
            .header("alg", "none")
            .subject("subject")
            .apply { claims.forEach { (name, value) -> claim(name, value) } }
            .build()

    private fun authoritiesOf(jwt: Jwt): List<String?> = converter.convert(jwt).authorities.map { it.authority }

    @Test
    fun `should map realm roles to ROLE authorities`() {
        // Given
        val token = jwt("realm_access" to mapOf("roles" to listOf("user_app", "skill_reader")))

        // When
        val authorities = authoritiesOf(token)

        // Then
        assertThat(authorities).containsExactlyInAnyOrder("ROLE_user_app", "ROLE_skill_reader")
    }

    @Test
    fun `should map client scopes to SCOPE authorities`() {
        // Given
        val token = jwt("scope" to "profile reporting_read third_party_authentication", "azp" to "tola-data")

        // When
        val authorities = authoritiesOf(token)

        // Then
        assertThat(authorities).containsExactlyInAnyOrder(
            "SCOPE_profile",
            "SCOPE_reporting_read",
            "SCOPE_third_party_authentication"
        )
    }

    @Test
    fun `should grant FRONTEND_CLIENT authority to tokens issued to the frontend client`() {
        // Given
        val token = jwt("azp" to "app", "scope" to "openid email")

        // When
        val authorities = authoritiesOf(token)

        // Then
        assertThat(authorities).contains(AamAuthorities.FRONTEND_CLIENT)
    }

    @Test
    fun `should not grant FRONTEND_CLIENT authority to tokens issued to other clients`() {
        // Given
        val token = jwt("azp" to "some-api-client", "scope" to "reporting_read")

        // When
        val authorities = authoritiesOf(token)

        // Then
        assertThat(authorities).doesNotContain(AamAuthorities.FRONTEND_CLIENT)
    }

    @Test
    fun `should not grant FRONTEND_CLIENT authority if no frontend client is configured`() {
        // Given
        val converterWithoutFrontend = AamAuthenticationConverter(frontendClientId = "")
        val token = jwt("azp" to "", "scope" to "openid")

        // When
        val authorities = converterWithoutFrontend.convert(token).authorities.map { it.authority }

        // Then
        assertThat(authorities).doesNotContain(AamAuthorities.FRONTEND_CLIENT)
    }

    @Test
    fun `should return no authorities for a token without roles, scopes or client`() {
        // Given
        val token = jwt("realm_access" to mapOf("roles" to "not-a-list"))

        // When
        val authorities = authoritiesOf(token)

        // Then
        assertThat(authorities).isEmpty()
    }
}
