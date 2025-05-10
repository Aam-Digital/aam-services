package com.aamdigital.aambackendservice.common.domain

import com.aamdigital.aambackendservice.common.error.AamErrorCode
import com.aamdigital.aambackendservice.common.error.AamException
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Represents the input data needed, to fulfill the use case.
 * Usually implemented as data class.
 */
interface UseCaseRequest

/**
 * Represents the data outcome, if the use case was applied successfully.
 * Usually implemented as data class.
 */
interface UseCaseData

/**
 * The UseCaseOutcome will represent the result of a use case run.
 * It's always `Success` or `Failure` and will provide either `data` or `error details`
 */
sealed interface UseCaseOutcome<D : UseCaseData> {
    data class Success<D : UseCaseData>(
        val data: D
    ) : UseCaseOutcome<D>

    data class Failure<D : UseCaseData>(
        val errorCode: AamErrorCode,
        val errorMessage: String = "An unexpected error occurred while executing this use case.",
        val cause: Throwable? = null
    ) : UseCaseOutcome<D>
}

abstract class DomainUseCase<R : UseCaseRequest, D : UseCaseData> {

    protected val logger: Logger = LoggerFactory.getLogger(javaClass)

    enum class DomainError : AamErrorCode {
        UNHANDLED_EXCEPTION_IN_USE_CASE
    }

    /**
     * Implement your business use case here
     *
     * @throws AamException
     */
    protected abstract fun apply(request: R): UseCaseOutcome<D>

    /**
     * optional extend default error handling
     */
    protected open fun errorHandler(it: Throwable): UseCaseOutcome<D> = baseErrorHandler(it)

    private fun baseErrorHandler(
        it: Throwable
    ): UseCaseOutcome<D> {
        val errorCode: AamErrorCode = when (it) {
            is AamException -> {
                it.code
            }

            else -> {
                DomainError.UNHANDLED_EXCEPTION_IN_USE_CASE
            }
        }

        logger.debug("[{}] {}", errorCode, it.localizedMessage, it.cause)

        return UseCaseOutcome.Failure(
            errorMessage = it.localizedMessage,
            errorCode = errorCode,
            cause = it
        )
    }

    /**
     *  Execute the use case with errorHandler() in place.
     */
    fun run(request: R): UseCaseOutcome<D> {
        return try {
            apply(request)
        } catch (ex: Exception) {
            errorHandler(ex)
        }
    }
}
