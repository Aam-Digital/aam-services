package com.aamdigital.aambackendservice.reporting

import com.aamdigital.aambackendservice.common.domain.DomainUseCase
import com.aamdigital.aambackendservice.common.error.NotFoundException
import com.aamdigital.aambackendservice.common.security.AamAuthorities
import com.aamdigital.aambackendservice.reporting.report.controller.ReportController
import com.aamdigital.aambackendservice.reporting.report.core.ReportStorage
import com.aamdigital.aambackendservice.reporting.reportcalculation.controller.ReportCalculationController
import com.aamdigital.aambackendservice.reporting.reportcalculation.core.ReportCalculationStorage
import com.aamdigital.aambackendservice.reporting.webhook.WebhookAuthenticationType
import com.aamdigital.aambackendservice.reporting.webhook.WebhookTarget
import com.aamdigital.aambackendservice.reporting.webhook.controller.CreateWebhookRequestDto
import com.aamdigital.aambackendservice.reporting.webhook.controller.WebhookAuthenticationWriteDto
import com.aamdigital.aambackendservice.reporting.webhook.controller.WebhookController
import com.aamdigital.aambackendservice.reporting.webhook.storage.WebhookStorage
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
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import java.security.Principal

/**
 * Checks the method security of the reporting controllers:
 * [RequiresReportingReadAccess] on class level, overridden by [RequiresReportingWriteAccess] for write endpoints.
 */
@SpringJUnitConfig(ReportingAccessTest.Config::class)
class ReportingAccessTest {
    @Configuration
    @EnableMethodSecurity
    class Config {
        private val notFound = NotFoundException(code = DomainUseCase.DomainError.UNHANDLED_EXCEPTION_IN_USE_CASE)

        @Bean
        fun reportStorage(): ReportStorage =
            mock {
                on { fetchAllReports(any()) } doReturn emptyList()
                on { fetchReport(any()) } doThrow notFound
            }

        @Bean
        fun reportCalculationStorage(): ReportCalculationStorage =
            mock {
                on { fetchReportCalculation(any()) } doThrow notFound
            }

        @Bean
        fun webhookStorage(): WebhookStorage =
            mock {
                on { fetchAllWebhooks() } doReturn emptyList()
                on { fetchWebhook(any()) } doThrow notFound
                on { createWebhook(any()) } doThrow notFound
            }

        @Bean
        fun reportController(reportStorage: ReportStorage) = ReportController(reportStorage = reportStorage)

        @Bean
        fun reportCalculationController(
            reportStorage: ReportStorage,
            reportCalculationStorage: ReportCalculationStorage
        ) = ReportCalculationController(
            reportStorage = reportStorage,
            reportCalculationStorage = reportCalculationStorage,
            fileStorage = mock(),
            createReportCalculationUseCase = mock(),
            objectMapper = ObjectMapper()
        )

        @Bean
        fun webhookController(webhookStorage: WebhookStorage) =
            WebhookController(webhookStorage = webhookStorage, addWebhookSubscriptionUseCase = mock())
    }

    @Autowired
    private lateinit var reportController: ReportController

    @Autowired
    private lateinit var reportCalculationController: ReportCalculationController

    @Autowired
    private lateinit var webhookController: WebhookController

    private val principal = Principal { "api-client" }

    private val webhookRequest =
        CreateWebhookRequestDto(
            label = "webhook",
            target = WebhookTarget(method = "POST", url = "https://example.com/webhook"),
            authentication = WebhookAuthenticationWriteDto(type = WebhookAuthenticationType.API_KEY, apiKey = "key")
        )

    private val readEndpoints: List<() -> ResponseEntity<*>> =
        listOf(
            { reportController.fetchReports() },
            { reportCalculationController.fetchReportCalculation(calculationId = "ReportCalculation:1") },
            { webhookController.fetchWebhooks(principal) },
            { webhookController.fetchWebhook(webhookId = "Webhook:1", principal = principal) }
        )

    private val writeEndpoints: List<() -> ResponseEntity<*>> =
        listOf(
            { reportCalculationController.startCalculation(reportId = "ReportConfig:1", from = null, to = null) },
            { webhookController.storeWebhook(request = webhookRequest, principal = principal) },
            { webhookController.registerReportNotification(webhookId = "Webhook:1", reportId = "ReportConfig:1") },
            { webhookController.unregisterReportNotification(webhookId = "Webhook:1", reportId = "ReportConfig:1") }
        )

    private fun assertAllowed(endpoints: List<() -> ResponseEntity<*>>) =
        endpoints.forEach { endpoint ->
            assertThat(endpoint().statusCode).isIn(HttpStatus.OK, HttpStatus.NOT_FOUND)
        }

    private fun assertDenied(endpoints: List<() -> ResponseEntity<*>>) =
        endpoints.forEach { endpoint ->
            assertThatThrownBy { endpoint() }.isInstanceOf(AccessDeniedException::class.java)
        }

    @Test
    @WithMockUser(authorities = ["SCOPE_reporting_read"])
    fun `should allow only read endpoints with the reporting_read scope`() {
        // When / Then
        assertAllowed(readEndpoints)
        assertDenied(writeEndpoints)
    }

    @Test
    @WithMockUser(authorities = ["SCOPE_reporting_write"])
    fun `should allow only write endpoints with the reporting_write scope`() {
        // When / Then
        assertAllowed(writeEndpoints)
        assertDenied(readEndpoints)
    }

    @Test
    @WithMockUser(authorities = [AamAuthorities.FRONTEND_CLIENT])
    fun `should allow users of the frontend app all endpoints without scopes`() {
        // When / Then
        assertAllowed(readEndpoints)
        assertAllowed(writeEndpoints)
    }

    @Test
    @WithMockUser(authorities = ["ROLE_user_app", "SCOPE_profile", "SCOPE_third_party_authentication"])
    fun `should deny API clients without reporting scopes regardless of their roles and other scopes`() {
        // When / Then
        assertDenied(readEndpoints)
        assertDenied(writeEndpoints)
    }
}
