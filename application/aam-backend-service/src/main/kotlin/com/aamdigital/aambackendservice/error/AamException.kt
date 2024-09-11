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
    message: String = "Unspecific InternalServerException",
    cause: Throwable? = null,
    code: String = "INTERNAL_SERVER_ERROR"
) : AamException(message, cause, code)

class ExternalSystemException(
    message: String = "Unspecific ExternalSystemException",
    cause: Throwable? = null,
    code: String = "EXTERNAL_SYSTEM_ERROR"
) : AamException(message, cause, code)

class NetworkException(
    message: String = "Unspecific NetworkException",
    cause: Throwable? = null,
    code: String = "NETWORK_EXCEPTION"
) : AamException(message, cause, code)

class InvalidArgumentException(
    message: String = "Unspecific InvalidArgumentException",
    cause: Throwable? = null,
    code: String = "BAD_REQUEST"
) : AamException(message, cause, code)

class UnauthorizedAccessException(
    message: String = "Unspecific UnauthorizedAccessException",
    cause: Throwable? = null,
    code: String = "UNAUTHORIZED"
) : AamException(message, cause, code)

class ForbiddenAccessException(
    message: String = "Unspecific ForbiddenAccessException",
    cause: Throwable? = null,
    code: String = "FORBIDDEN"
) : AamException(message, cause, code)

class NotFoundException(
    message: String = "Unspecific NotFoundException",
    cause: Throwable? = null,
    code: String = "NOT_FOUND"
) : AamException(message, cause, code)
