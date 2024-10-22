package com.aamdigital.aambackendservice.security

import com.aamdigital.aambackendservice.error.HttpErrorDto
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.core.AuthenticationException
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint
import org.springframework.security.web.AuthenticationEntryPoint

class AamAuthenticationEntryPoint(
    private val parentEntryPoint: BearerTokenAuthenticationEntryPoint,
    private val objectMapper: ObjectMapper,
) : AuthenticationEntryPoint {
    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException
    ) {
        parentEntryPoint.commence(request, response, authException)

        var errorCode = HttpStatus.UNAUTHORIZED.value().toString()

        if (authException is OAuth2AuthenticationException) {
            errorCode = authException.error.errorCode
        }

        response.addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)

        response.writer.println(
            objectMapper.writeValueAsString(
                HttpErrorDto(
                    errorCode = errorCode,
                    errorMessage = authException.message.toString()
                )
            )
        )
    }
}
