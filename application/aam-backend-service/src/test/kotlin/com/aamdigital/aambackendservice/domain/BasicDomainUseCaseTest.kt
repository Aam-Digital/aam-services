package com.aamdigital.aambackendservice.domain

import com.aamdigital.aambackendservice.error.InternalServerException
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

@ExtendWith(MockitoExtension::class)
class BasicDomainUseCaseTest {

    enum class BasicUseCaseErrorCode : UseCaseErrorCode {
        UNKNOWN
    }

    class BasicTestUseCase : DomainUseCase<UseCaseRequest, UseCaseData, UseCaseErrorCode> {
        override fun apply(request: UseCaseRequest): Mono<UseCaseOutcome<UseCaseData, UseCaseErrorCode>> {
            throw InternalServerException("error")
        }

        override fun handleError(it: Throwable): Mono<UseCaseOutcome<UseCaseData, UseCaseErrorCode>> {
            return Mono.just(
                UseCaseOutcome.Failure(errorCode = BasicUseCaseErrorCode.UNKNOWN, cause = it)
            )
        }
    }

    private val useCase = BasicTestUseCase()

    @Test
    fun `should catch exception in UseCaseOutcome when call execute()`() {
        val request: UseCaseRequest = object : UseCaseRequest {}

        StepVerifier.create(
            useCase.execute(request)
        ).assertNext {
            Assertions.assertThat(it).isInstanceOf(UseCaseOutcome.Failure::class.java)
        }
    }
}
