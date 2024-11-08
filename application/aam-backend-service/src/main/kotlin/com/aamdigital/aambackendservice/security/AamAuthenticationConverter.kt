package com.aamdigital.aambackendservice.security

import org.springframework.core.convert.converter.Converter
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Component

@Component
class AamAuthenticationConverter : Converter<Jwt, AbstractAuthenticationToken> {
    override fun convert(source: Jwt): AbstractAuthenticationToken {
        return JwtAuthenticationToken(source, getClientAuthorities(source))
    }

    private fun getClientAuthorities(jwt: Jwt): Collection<GrantedAuthority> {
        val realmAccessClaim = jwt.getClaimAsMap("realm_access") ?: return emptyList()

        val roles: List<String> = if (realmAccessClaim.containsKey("roles")) {
            when (val rolesClaim = realmAccessClaim["roles"]) {
                is List<*> -> rolesClaim.filterIsInstance<String>()
                else -> emptyList()
            }
        } else {
            emptyList()
        }

        return roles
            .filter { it.startsWith("aam_") }
            .map {
                SimpleGrantedAuthority("ROLE_$it")
            }
    }
}
