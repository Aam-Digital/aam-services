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

/**
 * Base class of a use case: subclasses implement [apply], callers call [run].
 *
 * [run] reports a failure through its outcome rather than by throwing. A Failure that [apply]
 * returns is passed through as it is, and an exception that [apply] throws becomes a
 * [UseCaseOutcome.Failure] with that exception as its `cause` (unless an [errorHandler] override
 * rethrows it).
 *
 * Logging a Failure is the job of whoever calls [run], not of the use case: only the caller knows
 * what was being done and whether the failure is expected, so it picks the level. ERROR is what
 * reaches Sentry as an event, WARN stays in the logs without alerting, and INFO or below suits an
 * expected outcome such as invalid input. A production setup built from
 * `templates/aam-backend-service/application.template.env` logs this service at WARN, though, and
 * drops INFO and below, so a Failure that must be visible in production logs needs WARN or above.
 *
 * A caller therefore logs every Failure it receives, with the error code and message as
 * placeholder arguments and the cause as the last argument so the stack trace is kept, or hands it
 * on to something that logs it (a rethrow into `ScheduledJobBackoff`, an `OutboxDeliveryResult`
 * for the outbox). [run] logs the exception only at DEBUG, so a Failure the caller drops leaves no
 * trace in production.
 */
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
     * Turns an exception from [apply] into the outcome [run] returns: a Failure with the code of an
     * [AamException], or [DomainError.UNHANDLED_EXCEPTION_IN_USE_CASE] for any other exception.
     *
     * An override can treat particular exceptions differently, e.g. rethrow one that its caller
     * handles itself, and pass the rest on to this default. Like the default, an override leaves
     * logging the Failure to the caller.
     */
    protected open fun errorHandler(it: Throwable): UseCaseOutcome<D> = baseErrorHandler(it)

    private fun baseErrorHandler(it: Throwable): UseCaseOutcome<D> {
        val errorCode: AamErrorCode =
            when (it) {
                is AamException -> {
                    it.code
                }

                else -> {
                    DomainError.UNHANDLED_EXCEPTION_IN_USE_CASE
                }
            }

        logger.debug("[{}] {}", errorCode, it.localizedMessage, it)

        return UseCaseOutcome.Failure(
            // an exception can come without a message, e.g. the NullPointerException of Kotlin's !!
            errorMessage = it.localizedMessage ?: it.javaClass.name,
            errorCode = errorCode,
            cause = it
        )
    }

    /**
     * Executes the use case, turning an exception from [apply] into a Failure with [errorHandler].
     * The caller logs a Failure it gets back; see the class documentation.
     */
    fun run(request: R): UseCaseOutcome<D> =
        try {
            apply(request)
        } catch (ex: Exception) {
            errorHandler(ex)
        }
}
