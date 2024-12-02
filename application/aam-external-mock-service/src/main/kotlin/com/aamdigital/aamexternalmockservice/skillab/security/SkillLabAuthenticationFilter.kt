package com.aamdigital.aamexternalmockservice.skillab.security

import com.aamdigital.aamexternalmockservice.skillab.error.RestErrorHandler
import com.aamdigital.aamexternalmockservice.skillab.error.SkillLabErrorResponseDto
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter
import java.io.IOException

class SkillLabAuthenticationFilter(
    private val restErrorHandler: RestErrorHandler,
    private val objectMapper: ObjectMapper,
    private val skillLabAuthenticationService: SkillLabAuthenticationService,
) : OncePerRequestFilter() {
    @Throws(IOException::class, ServletException::class)
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        try {
            SecurityContextHolder.getContext().authentication = skillLabAuthenticationService.getAuthentication(request)
        } catch (ex: Exception) {
            val error = restErrorHandler.getSkillLabError(ex)
            response.status = HttpStatus.valueOf(error.code).value();
            response.writer.write(
                objectMapper.writeValueAsString(
                    SkillLabErrorResponseDto(
                        error = error
                    )
                )
            )
            return
        }
        filterChain.doFilter(request, response)
    }
}
