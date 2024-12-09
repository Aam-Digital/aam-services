package com.aamdigital.aamexternalmockservice.skillab.error

import org.springframework.security.authentication.BadCredentialsException
import org.springframework.stereotype.Component

data class SkillLabErrorResponseDto(
    val error: SkillLabError,
)

sealed class SkillLabError(
    val code: Int,
    val title: String,
    val message: String,
    val detail: String,
) {
    class Unauthorized : SkillLabError(
        code = 401,
        title = "Unauthorized",
        message = "The provided API key was missing or invalid",
        detail = "Ensure that you are using a valid API key",
    )

    class Forbidden : SkillLabError(
        code = 403,
        title = "Forbidden",
        message = "Access was denied for the requested resource",
        detail = "Ensure that the API key you are using provides access to the\n" +
                "requested resource",
    )

    class NotFound : SkillLabError(
        code = 404,
        title = "Page not found",
        message = "The page you were looking for doesn’t exist",
        detail = "Ensure that the path you are using is valid",
    )

    class TooManyRequests : SkillLabError(
        code = 429,
        title = "Too many requests",
        message = "The request limit has been reached",
        detail = "",
    )

    class InternalServerError : SkillLabError(
        code = 500,
        title = "Internal server error",
        message = "No message available",
        detail = "",
    )
}

@Component
class RestErrorHandler {
    fun getSkillLabError(ex: Exception): SkillLabError {
        return when (ex) {
            is BadCredentialsException -> return SkillLabError.Unauthorized()
            else -> SkillLabError.InternalServerError()
        }
    }
}


