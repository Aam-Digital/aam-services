package com.aamdigital.aamintegration.error

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

class IOException(
    message: String = "Unspecific IOException",
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
