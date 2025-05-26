package com.aamdigital.aambackendservice.common.security

import com.aamdigital.aambackendservice.common.error.HttpErrorDto
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.core.AuthenticationException
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.oauth2.server.resource.BearerTokenErrorCodes
import org.springframework.security.web.AuthenticationEntryPoint

class AamAuthenticationEntryPoint(
    private val objectMapper: ObjectMapper,
) : AuthenticationEntryPoint {
    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException
    ) {
        response.status = HttpStatus.UNAUTHORIZED.value()
        response.addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        
        // Build WWW-Authenticate header
        val parameters: MutableMap<String, String?> = LinkedHashMap()
        var errorCode = HttpStatus.UNAUTHORIZED.value().toString()
        var errorMessage = authException.message ?: "Unauthorized"
        
        if (authException is OAuth2AuthenticationException) {
            errorCode = authException.error.errorCode
            parameters["error"] = errorCode
            parameters["error_description"] = errorMessage
            parameters["error_uri"] = "https://tools.ietf.org/html/rfc6750#section-3.1"
        } else {
            parameters["error"] = BearerTokenErrorCodes.INVALID_TOKEN
            parameters["error_description"] = errorMessage
        }
        
        val wwwAuthenticate = SecurityHeaderUtils.computeWWWAuthenticateHeaderValue(parameters)
        response.addHeader(HttpHeaders.WWW_AUTHENTICATE, wwwAuthenticate)

        response.writer.println(
            objectMapper.writeValueAsString(
                HttpErrorDto(
                    errorCode = errorCode,
                    errorMessage = errorMessage
                )
            )
        )
    }
}
