package com.aamdigital.aambackendservice.reporting.webhook.storage

import com.aamdigital.aambackendservice.common.crypto.core.CryptoConfig
import com.aamdigital.aambackendservice.common.crypto.core.CryptoService
import com.aamdigital.aambackendservice.common.domain.DomainReference
import com.aamdigital.aambackendservice.reporting.webhook.WebhookAuthenticationType
import com.aamdigital.aambackendservice.reporting.webhook.WebhookTarget
import com.aamdigital.aambackendservice.reporting.webhook.controller.WebhookAuthenticationWriteDto
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Duration
import java.time.Instant

@ExtendWith(MockitoExtension::class)
class DefaultWebhookStorageTest {
    @Mock
    lateinit var webhookRepository: WebhookRepository

    // cheap and deterministic - construct for real rather than mocking it
    private val cryptoService = CryptoService(CryptoConfig(secret = "test-secret"))

    private lateinit var storage: DefaultWebhookStorage

    @BeforeEach
    fun setUp() {
        // cheap and deterministic - construct for real rather than mocking it
        storage =
            DefaultWebhookStorage(
                webhookRepository,
                cryptoService,
                WebhookSubscriptionCache(webhookRepository, Duration.ZERO)
            )
    }

    @Test
    fun `createWebhook stores the entity with a createdAt timestamp`() {
        val before = Instant.now()

        storage.createWebhook(
            CreateWebhookRequest(
                user = "user-1",
                label = "Test Webhook",
                target = WebhookTarget(method = "GET", url = "https://example.com/hook"),
                authentication =
                    WebhookAuthenticationWriteDto(
                        type = WebhookAuthenticationType.API_KEY,
                        apiKey = "secret"
                    )
            )
        )

        val storedEntity = argumentCaptor<WebhookEntity>()
        verify(webhookRepository).storeWebhook(storedEntity.capture())

        assertThat(storedEntity.firstValue.createdAt).isNotNull
        assertThat(storedEntity.firstValue.createdAt).isAfterOrEqualTo(before)
    }

    @Test
    fun `fetchWebhook maps the stored createdAt onto the domain object`() {
        val createdAt = Instant.parse("2026-01-01T00:00:00Z")
        val encryptedSecret = cryptoService.encrypt("secret")
        whenever(webhookRepository.fetchWebhook(webhookRef = DomainReference("Webhook:1")))
            .thenReturn(
                WebhookEntity(
                    id = "Webhook:1",
                    label = "Test Webhook",
                    target = WebhookTarget(method = "GET", url = "https://example.com/hook"),
                    authentication =
                        WebhookAuthenticationEntity(
                            type = WebhookAuthenticationType.API_KEY,
                            iv = encryptedSecret.iv,
                            data = encryptedSecret.data
                        ),
                    owner = WebhookOwner(creator = "user-1"),
                    createdAt = createdAt
                )
            )

        val webhook = storage.fetchWebhook(DomainReference("Webhook:1"))

        assertThat(webhook.createdAt).isEqualTo(createdAt)
    }

    @Test
    fun `fetchWebhook maps a missing createdAt as null for webhooks predating the field`() {
        val encryptedSecret = cryptoService.encrypt("secret")
        whenever(webhookRepository.fetchWebhook(webhookRef = DomainReference("Webhook:1")))
            .thenReturn(
                WebhookEntity(
                    id = "Webhook:1",
                    label = "Test Webhook",
                    target = WebhookTarget(method = "GET", url = "https://example.com/hook"),
                    authentication =
                        WebhookAuthenticationEntity(
                            type = WebhookAuthenticationType.API_KEY,
                            iv = encryptedSecret.iv,
                            data = encryptedSecret.data
                        ),
                    owner = WebhookOwner(creator = "user-1")
                )
            )

        val webhook = storage.fetchWebhook(DomainReference("Webhook:1"))

        assertThat(webhook.createdAt).isNull()
    }
}
