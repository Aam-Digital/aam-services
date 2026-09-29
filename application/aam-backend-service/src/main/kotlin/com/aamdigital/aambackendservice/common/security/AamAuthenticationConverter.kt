package com.aamdigital.aambackendservice.common.security

import org.springframework.beans.factory.annotation.Value
import org.springframework.core.convert.converter.Converter
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter
import org.springframework.stereotype.Component

/**
 * Authorities derived from an access token, to be checked with `@PreAuthorize`.
 */
object AamAuthorities {
    /** Prefix of authorities for the realm roles in the `realm_access.roles` claim. */
    const val ROLE_PREFIX = "ROLE_"

    /** Prefix of authorities for the client scopes in the `scope` claim. */
    const val SCOPE_PREFIX = "SCOPE_"

    /**
     * Granted to tokens issued to the Aam Digital frontend client (`azp` claim),
     * i.e. to users working in the app rather than to an external API client.
     */
    const val FRONTEND_CLIENT = "FRONTEND_CLIENT"
}

/**
 * Maps a validated JWT to the [AamAuthorities] available for authorization checks:
 * - `ROLE_<role>` for every realm role in `realm_access.roles`
 * - `SCOPE_<scope>` for every client scope in the `scope` claim
 * - [AamAuthorities.FRONTEND_CLIENT] if the token was issued to the frontend client
 */
@Component
class AamAuthenticationConverter(
    @Value("\${aam-security.frontend-client-id:app}")
    private val frontendClientId: String
) : Converter<Jwt, AbstractAuthenticationToken> {
    private val scopeAuthoritiesConverter = JwtGrantedAuthoritiesConverter()

    override fun convert(source: Jwt): AbstractAuthenticationToken =
        JwtAuthenticationToken(
            source,
            getRealmRoleAuthorities(source) + getScopeAuthorities(source) + getClientAuthorities(source)
        )

    private fun getRealmRoleAuthorities(jwt: Jwt): Collection<GrantedAuthority> {
        val realmAccessClaim = jwt.getClaimAsMap("realm_access") ?: return emptyList()

        val roles: List<String> =
            if (realmAccessClaim.containsKey("roles")) {
                when (val rolesClaim = realmAccessClaim["roles"]) {
                    is List<*> -> rolesClaim.filterIsInstance<String>()
                    else -> emptyList()
                }
            } else {
                emptyList()
            }

        return roles
            .map {
                SimpleGrantedAuthority("${AamAuthorities.ROLE_PREFIX}$it")
            }
    }

    private fun getScopeAuthorities(jwt: Jwt): Collection<GrantedAuthority> =
        scopeAuthoritiesConverter.convert(jwt) ?: emptyList()

    private fun getClientAuthorities(jwt: Jwt): Collection<GrantedAuthority> =
        if (frontendClientId.isNotBlank() && jwt.getClaimAsString("azp") == frontendClientId) {
            listOf(SimpleGrantedAuthority(AamAuthorities.FRONTEND_CLIENT))
        } else {
            emptyList()
        }
}
