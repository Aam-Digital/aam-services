package com.aamdigital.aambackendservice.common.domain

import com.aamdigital.aambackendservice.common.error.AamErrorCode
import com.aamdigital.aambackendservice.common.error.InternalServerException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

enum class TestErrorCode : AamErrorCode {
    TEST_EXCEPTION
}

class BasicDomainUseCaseTest {
    class BasicTestUseCase(
        private val exception: Exception =
            InternalServerException(
                message = "error",
                code = TestErrorCode.TEST_EXCEPTION
            )
    ) : DomainUseCase<UseCaseRequest, UseCaseData>() {
        override fun apply(request: UseCaseRequest): UseCaseOutcome<UseCaseData> = throw exception
    }

    private val request: UseCaseRequest = object : UseCaseRequest {}

    @Test
    fun `should catch exception in UseCaseOutcome when call apply()`() {
        // Given
        val useCase = BasicTestUseCase()

        // When
        val response = useCase.run(request)

        // Then
        assertThat(response).isInstanceOf(UseCaseOutcome.Failure::class.java)
        assertThat((response as UseCaseOutcome.Failure).errorCode)
            .isEqualTo(TestErrorCode.TEST_EXCEPTION)
    }

    @Test
    fun `should return a Failure for an exception without a message instead of throwing`() {
        // Given - what Kotlin's !! throws
        val exception = NullPointerException()
        val useCase = BasicTestUseCase(exception)

        // When
        val response = useCase.run(request)

        // Then
        assertThat(response).isInstanceOf(UseCaseOutcome.Failure::class.java)
        val failure = response as UseCaseOutcome.Failure
        assertThat(failure.errorCode).isEqualTo(DomainUseCase.DomainError.UNHANDLED_EXCEPTION_IN_USE_CASE)
        assertThat(failure.errorMessage).isEqualTo("java.lang.NullPointerException")
        assertThat(failure.cause).isSameAs(exception)
    }
}
