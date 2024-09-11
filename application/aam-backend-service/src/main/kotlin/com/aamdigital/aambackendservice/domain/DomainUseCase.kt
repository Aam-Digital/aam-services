package com.aamdigital.aambackendservice.domain

import reactor.core.publisher.Mono


interface UseCaseRequest
interface UseCaseData
interface UseCaseErrorCode

sealed interface UseCaseOutcome<D : UseCaseData, E : UseCaseErrorCode> {
    data class Success<D : UseCaseData, E : UseCaseErrorCode>(
        val outcome: D
    ) : UseCaseOutcome<D, E>

    data class Failure<D : UseCaseData, E : UseCaseErrorCode>(
        val errorCode: E,
        val errorMessage: String? = "An unspecific error occurred, while executing this use case",
        val cause: Throwable? = null
    ) : UseCaseOutcome<D, E>
}

interface DomainUseCase<R : UseCaseRequest, D : UseCaseData, E : UseCaseErrorCode> {
    fun apply(request: R): Mono<UseCaseOutcome<D, E>>
    fun handleError(it: Throwable): Mono<UseCaseOutcome<D, E>>

    fun execute(request: R): Mono<UseCaseOutcome<D, E>> {
        return try {
            apply(request)
                .onErrorResume {
                    handleError(it)
                }
        } catch (ex: Exception) {
            handleError(ex)
        }
    }
}
