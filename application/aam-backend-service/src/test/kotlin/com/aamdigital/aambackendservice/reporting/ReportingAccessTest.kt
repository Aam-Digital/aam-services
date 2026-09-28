package com.aamdigital.aambackendservice.reporting

import com.aamdigital.aambackendservice.common.domain.DomainUseCase
import com.aamdigital.aambackendservice.common.error.NotFoundException
import com.aamdigital.aambackendservice.common.security.AamAuthorities
import com.aamdigital.aambackendservice.reporting.report.controller.ReportController
import com.aamdigital.aambackendservice.reporting.report.core.ReportStorage
import com.aamdigital.aambackendservice.reporting.reportcalculation.controller.ReportCalculationController
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig

/**
 * Checks the method security of the reporting controllers:
 * [RequiresReportingReadAccess] on class level, overridden by [RequiresReportingWriteAccess] for write endpoints.
 */
@SpringJUnitConfig(ReportingAccessTest.Config::class)
class ReportingAccessTest {
    @Configuration
    @EnableMethodSecurity
    class Config {
        @Bean
        fun reportStorage(): ReportStorage =
            mock {
                on { fetchAllReports(any()) } doReturn emptyList()
                on { fetchReport(any()) } doThrow
                    NotFoundException(code = DomainUseCase.DomainError.UNHANDLED_EXCEPTION_IN_USE_CASE)
            }

        @Bean
        fun reportController(reportStorage: ReportStorage) = ReportController(reportStorage = reportStorage)

        @Bean
        fun reportCalculationController(reportStorage: ReportStorage) =
            ReportCalculationController(
                reportStorage = reportStorage,
                reportCalculationStorage = mock(),
                fileStorage = mock(),
                createReportCalculationUseCase = mock(),
                objectMapper = ObjectMapper()
            )
    }

    @Autowired
    private lateinit var reportController: ReportController

    @Autowired
    private lateinit var reportCalculationController: ReportCalculationController

    private fun listReports() = reportController.fetchReports()

    private fun startCalculation() =
        reportCalculationController.startCalculation(reportId = "ReportConfig:1", from = null, to = null)

    @Test
    @WithMockUser(authorities = ["SCOPE_reporting_read"])
    fun `should allow reading with the reporting_read scope`() {
        // When
        val response = listReports()

        // Then
        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
    }

    @Test
    @WithMockUser(authorities = ["SCOPE_reporting_write"])
    fun `should deny reading with only the reporting_write scope`() {
        // When / Then
        assertThatThrownBy { listReports() }.isInstanceOf(AccessDeniedException::class.java)
    }

    @Test
    @WithMockUser(authorities = ["SCOPE_reporting_write"])
    fun `should allow starting a calculation with the reporting_write scope`() {
        // When
        val response = startCalculation()

        // Then
        assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
    }

    @Test
    @WithMockUser(authorities = ["SCOPE_reporting_read"])
    fun `should deny starting a calculation with only the reporting_read scope`() {
        // When / Then
        assertThatThrownBy { startCalculation() }.isInstanceOf(AccessDeniedException::class.java)
    }

    @Test
    @WithMockUser(authorities = [AamAuthorities.FRONTEND_CLIENT])
    fun `should allow users of the frontend app to read and start calculations without scopes`() {
        // When
        val listResponse = listReports()
        val startResponse = startCalculation()

        // Then
        assertThat(listResponse.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(startResponse.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
    }

    @Test
    @WithMockUser(authorities = ["ROLE_user_app", "SCOPE_profile"])
    fun `should deny API clients without reporting scopes regardless of their roles`() {
        // When / Then
        assertThatThrownBy { listReports() }.isInstanceOf(AccessDeniedException::class.java)
        assertThatThrownBy { startCalculation() }.isInstanceOf(AccessDeniedException::class.java)
    }
}
