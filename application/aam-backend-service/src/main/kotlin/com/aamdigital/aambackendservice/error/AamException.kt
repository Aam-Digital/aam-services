package com.aamdigital.aambackendservice.error

sealed class AamException(
    message: String,
    cause: Throwable? = null,
    val code: String = DEFAULT_ERROR_CODE
) : Exception(message, cause) {
    companion object {
        private const val DEFAULT_ERROR_CODE = "AAM-GENERAL"
    }
}

class InternalServerException(
    message: String = "",
    cause: Throwable? = null,
    code: String = "INTERNAL_SERVER_ERROR"
) : AamException(message, cause, code)

class ExternalSystemException(
    message: String = "",
    cause: Throwable? = null,
    code: String = "EXTERNAL_SYSTEM_ERROR"
) : AamException(message, cause, code)

class InvalidArgumentException(
    message: String = "",
    cause: Throwable? = null,
    code: String = "BAD_REQUEST"
) : AamException(message, cause, code)

class UnauthorizedAccessException(
    message: String = "",
    cause: Throwable? = null,
    code: String = "UNAUTHORIZED"
) : AamException(message, cause, code)

class ForbiddenAccessException(
    message: String = "",
    cause: Throwable? = null,
    code: String = "FORBIDDEN"
) : AamException(message, cause, code)

class NotFoundException(
    message: String = "",
    cause: Throwable? = null,
    code: String = "NOT_FOUND"
) : AamException(message, cause, code)
