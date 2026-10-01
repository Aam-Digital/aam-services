package com.aamdigital.aambackendservice.reporting.webhook.controller

import com.aamdigital.aambackendservice.common.domain.DomainReference
import com.aamdigital.aambackendservice.common.error.HttpErrorDto
import com.aamdigital.aambackendservice.reporting.webhook.WebhookAuthenticationType
import com.aamdigital.aambackendservice.reporting.webhook.WebhookTarget
import com.aamdigital.aambackendservice.reporting.webhook.storage.CreateWebhookRequest
import com.aamdigital.aambackendservice.reporting.webhook.storage.WebhookStorage
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken

class WebhookControllerTest {
    private val webhookStorage = mock<WebhookStorage>()

    private lateinit var controller: WebhookController

    private val request =
        CreateWebhookRequestDto(
            label = "webhook",
            target = WebhookTarget(method = "POST", url = "https://example.com/webhook"),
            authentication = WebhookAuthenticationWriteDto(type = WebhookAuthenticationType.API_KEY, apiKey = "key")
        )

    @BeforeEach
    fun setUp() {
        controller = WebhookController(webhookStorage = webhookStorage, addWebhookSubscriptionUseCase = mock())
    }

    private fun authentication(configure: Jwt.Builder.() -> Unit): JwtAuthenticationToken =
        JwtAuthenticationToken(
            Jwt
                .withTokenValue("token")
                .header("alg", "none")
                .claim("azp", "api-client")
                .apply(configure)
                .build()
        )

    @Test
    fun `should create the webhook for the subject of the token`() {
        // Given
        whenever(webhookStorage.createWebhook(any())).thenReturn(DomainReference("Webhook:1"))

        // When
        val response = controller.storeWebhook(request, authentication { subject("user-1") })

        // Then
        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        verify(webhookStorage).createWebhook(
            CreateWebhookRequest(
                user = "user-1",
                label = request.label,
                target = request.target,
                authentication = request.authentication
            )
        )
    }

    @Test
    fun `should not create a webhook for a token without subject`() {
        // When
        val response = controller.storeWebhook(request, authentication { claim("username", "user-1") })

        // Then
        assertThat(response.statusCode).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR)
        assertThat((response.body as HttpErrorDto).errorMessage).isEqualTo("No subject found in the token.")
        verify(webhookStorage, never()).createWebhook(any())
    }
}
