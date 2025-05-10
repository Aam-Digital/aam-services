package com.aamdigital.aambackendservice.common.domain

import com.aamdigital.aambackendservice.common.domain.DomainUseCase
import com.aamdigital.aambackendservice.common.domain.UseCaseData
import com.aamdigital.aambackendservice.common.domain.UseCaseOutcome
import com.aamdigital.aambackendservice.common.domain.UseCaseRequest
import com.aamdigital.aambackendservice.common.error.AamErrorCode
import com.aamdigital.aambackendservice.common.error.InternalServerException
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension

enum class TestErrorCode : AamErrorCode {
    TEST_EXCEPTION,
}

@ExtendWith(MockitoExtension::class)
class BasicDomainUseCaseTest {

    class BasicTestUseCase : DomainUseCase<UseCaseRequest, UseCaseData>() {


        override fun apply(request: UseCaseRequest): UseCaseOutcome<UseCaseData> {
            throw InternalServerException(
                message = "error",
                code = TestErrorCode.TEST_EXCEPTION
            )
        }
    }

    private val useCase = BasicTestUseCase()

    @Test
    fun `should catch exception in UseCaseOutcome when call apply()`() {
        val request: UseCaseRequest = object : UseCaseRequest {}

        val response = useCase.run(request)

        Assertions.assertThat(response).isInstanceOf(UseCaseOutcome.Failure::class.java)
        Assertions.assertThat((response as UseCaseOutcome.Failure).errorCode)
            .isEqualTo(TestErrorCode.TEST_EXCEPTION)
    }
}
