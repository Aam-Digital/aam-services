package com.aamdigital.aambackendservice.common.security

import com.aamdigital.aambackendservice.common.error.HttpErrorDto
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.oauth2.server.resource.BearerTokenErrorCodes
import org.springframework.security.oauth2.server.resource.authentication.AbstractOAuth2TokenAuthenticationToken
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.stereotype.Component

@Component
class AamAccessDeniedHandler(
    private val objectMapper: ObjectMapper,
) : AccessDeniedHandler {

    /**
     * Collect error details from the provided parameters and format according to RFC
     * 6750, specifically `error`, `error_description`, `error_uri`, and
     * `scope`.
     * @param request that resulted in an `AccessDeniedException`
     * @param response so that the user agent can be advised of the failure
     * @param accessDeniedException that caused the invocation
     */
    override fun handle(
        request: HttpServletRequest, response: HttpServletResponse,
        accessDeniedException: AccessDeniedException?
    ) {
        val parameters: MutableMap<String, String?> = LinkedHashMap()

        if (request.userPrincipal is AbstractOAuth2TokenAuthenticationToken<*>) {
            parameters["error"] = BearerTokenErrorCodes.INSUFFICIENT_SCOPE
            parameters["error_description"] =
                "The request requires higher privileges than provided by the access token."
            parameters["error_uri"] = "https://tools.ietf.org/html/rfc6750#section-3.1"
        }
        val wwwAuthenticate = SecurityHeaderUtils.computeWWWAuthenticateHeaderValue(parameters)

        response.addHeader(HttpHeaders.CONTENT_TYPE, "application/json")
        response.addHeader(HttpHeaders.WWW_AUTHENTICATE, wwwAuthenticate)
        response.status = HttpStatus.FORBIDDEN.value()

        response.writer.println(
            objectMapper.writeValueAsString(
                HttpErrorDto(
                    errorCode = parameters["error"] ?: "forbidden",
                    errorMessage = parameters["error_description"] ?: "access denied"
                )
            )
        )
    }
}

