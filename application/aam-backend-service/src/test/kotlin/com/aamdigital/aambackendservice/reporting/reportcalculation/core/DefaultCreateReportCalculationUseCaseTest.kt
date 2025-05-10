package com.aamdigital.aambackendservice.reporting.reportcalculation.core

import com.aamdigital.aambackendservice.common.error.InternalServerException
import com.aamdigital.aambackendservice.common.domain.DomainReference
import com.aamdigital.aambackendservice.common.domain.TestErrorCode
import com.aamdigital.aambackendservice.reporting.reportcalculation.queue.RabbitMqReportCalculationEventPublisher
import com.aamdigital.aambackendservice.reporting.reportcalculation.usecase.DefaultCreateReportCalculationUseCase
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
    lateinit var reportCalculationStorage: ReportCalculationStorage

    @Mock
    lateinit var reportCalculationEventPublisher: RabbitMqReportCalculationEventPublisher

    @BeforeEach
    fun setUp() {
        reset(reportCalculationStorage)
        service = DefaultCreateReportCalculationUseCase(reportCalculationStorage, reportCalculationEventPublisher)
    }

    @Test
    fun `should return Failure when ReportingStorage throws error`() {
        // given
        whenever(reportCalculationStorage.fetchReportCalculations(any()))
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
