package com.aamdigital.aambackendservice.error

interface AamErrorCode

sealed class AamException(
    message: String,
    cause: Throwable? = null,
    val code: AamErrorCode
) : Exception(message, cause)

class InternalServerException(
    message: String = "Unspecific InternalServerException",
    cause: Throwable? = null,
    code: AamErrorCode,
) : AamException(message, cause, code)

class ExternalSystemException(
    message: String = "Unspecific ExternalSystemException",
    cause: Throwable? = null,
    code: AamErrorCode,
) : AamException(message, cause, code)

class NetworkException(
    message: String = "Unspecific NetworkException",
    cause: Throwable? = null,
    code: AamErrorCode,
) : AamException(message, cause, code)

class InvalidArgumentException(
    message: String = "Unspecific InvalidArgumentException",
    cause: Throwable? = null,
    code: AamErrorCode,
) : AamException(message, cause, code)

class UnauthorizedAccessException(
    message: String = "Unspecific UnauthorizedAccessException",
    cause: Throwable? = null,
    code: AamErrorCode,
) : AamException(message, cause, code)

class ForbiddenAccessException(
    message: String = "Unspecific ForbiddenAccessException",
    cause: Throwable? = null,
    code: AamErrorCode,
) : AamException(message, cause, code)

class NotFoundException(
    message: String = "Unspecific NotFoundException",
    cause: Throwable? = null,
    code: AamErrorCode,
) : AamException(message, cause, code)
