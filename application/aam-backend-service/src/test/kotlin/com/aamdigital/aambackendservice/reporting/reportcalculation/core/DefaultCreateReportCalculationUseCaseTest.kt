package com.aamdigital.aambackendservice.reporting.reportcalculation.core

import com.aamdigital.aambackendservice.domain.DomainReference
import com.aamdigital.aambackendservice.domain.TestErrorCode
import com.aamdigital.aambackendservice.error.InternalServerException
import com.aamdigital.aambackendservice.reporting.report.core.ReportingStorage
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.reset
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
class DefaultCreateReportCalculationUseCaseTest {

    private lateinit var service: CreateReportCalculationUseCase

    @Mock
    lateinit var reportingStorage: ReportingStorage

    @BeforeEach
    fun setUp() {
        reset(reportingStorage)
        service = DefaultCreateReportCalculationUseCase(reportingStorage)
    }

    @Test
    fun `should return Failure when ReportingStorage throws error`() {
        // given
        whenever(reportingStorage.fetchCalculations(any()))
            .thenAnswer {
                throw InternalServerException(
                    message = "error",
                    code = TestErrorCode.TEST_EXCEPTION,
                    cause = null
                )
            }

        // when
        val response = service.createReportCalculation(
            CreateReportCalculationRequest(
                report = DomainReference("Report:1"),
                args = mutableMapOf()
            )
        )

        // then
        assertThat(response).isInstanceOf(CreateReportCalculationResult.Failure::class.java)
        Assertions.assertEquals(
            CreateReportCalculationResult.ErrorCode.INTERNAL_SERVER_ERROR,
            (response as CreateReportCalculationResult.Failure).errorCode
        )
    }
}
