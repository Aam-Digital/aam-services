package com.aamdigital.aambackendservice.common.domain

import com.aamdigital.aambackendservice.common.error.AamErrorCode
import com.aamdigital.aambackendservice.common.error.InternalServerException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

enum class TestErrorCode : AamErrorCode {
    TEST_EXCEPTION
}

class BasicDomainUseCaseTest {
    class BasicTestUseCase : DomainUseCase<UseCaseRequest, UseCaseData>() {
        override fun apply(request: UseCaseRequest): UseCaseOutcome<UseCaseData> =
            throw InternalServerException(
                message = "error",
                code = TestErrorCode.TEST_EXCEPTION
            )
    }

    private val useCase = BasicTestUseCase()

    @Test
    fun `should catch exception in UseCaseOutcome when call apply()`() {
        // Given
        val request: UseCaseRequest = object : UseCaseRequest {}

        // When
        val response = useCase.run(request)

        // Then
        assertThat(response).isInstanceOf(UseCaseOutcome.Failure::class.java)
        assertThat((response as UseCaseOutcome.Failure).errorCode)
            .isEqualTo(TestErrorCode.TEST_EXCEPTION)
    }
}
