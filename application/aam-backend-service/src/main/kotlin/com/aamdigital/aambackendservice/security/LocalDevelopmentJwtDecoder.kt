package com.aamdigital.aambackendservice.security

import com.nimbusds.jwt.JWTParser
import org.springframework.security.oauth2.jwt.BadJwtException
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtDecoders
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.stereotype.Component
import java.text.ParseException

@Component
class LocalDevelopmentJwtDecoder : JwtDecoder {
    private val issuerDecoderMapping: Map<String, JwtDecoder> = mapOf(
        "https://localhost/auth/realms/dummy-realm" to
                JwtDecoders.fromIssuerLocation("http://keycloak:8080/realms/dummy-realm"),
    )

    override fun decode(token: String): Jwt {
        val parsedJwt = parse(token)
        val decoder = issuerDecoderMapping[parsedJwt.jwtClaimsSet.issuer]
            ?: throw JwtException("Issuer not recognized: ${parsedJwt.jwtClaimsSet.issuer}")

        return decoder.decode(token)
    }

    private fun parse(token: String): com.nimbusds.jwt.JWT {
        try {
            return JWTParser.parse(token)
        } catch (ex: ParseException) {
            throw BadJwtException(
                String.format(
                    format = "An error occurred while attempting to decode the Jwt: %s", "Malformed token"
                ), ex
            )
        } catch (ex: Exception) {
            throw BadJwtException(
                String.format(format = "An error occurred while attempting to decode the Jwt: %s", ex.message), ex
            )
        }
    }
}
