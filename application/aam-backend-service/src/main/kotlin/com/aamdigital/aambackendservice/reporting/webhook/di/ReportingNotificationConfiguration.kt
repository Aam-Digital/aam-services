package com.aamdigital.aambackendservice.reporting.webhook.di

import com.aamdigital.aambackendservice.common.crypto.core.CryptoService
import com.aamdigital.aambackendservice.reporting.reportcalculation.core.CreateReportCalculationUseCase
import com.aamdigital.aambackendservice.reporting.reportcalculation.core.ReportCalculationStorage
import com.aamdigital.aambackendservice.reporting.webhook.core.AddWebhookSubscriptionUseCase
import com.aamdigital.aambackendservice.reporting.webhook.core.DefaultAddWebhookSubscriptionUseCase
import com.aamdigital.aambackendservice.reporting.webhook.core.DefaultTriggerWebhookUseCase
import com.aamdigital.aambackendservice.reporting.webhook.core.DefaultUriParser
import com.aamdigital.aambackendservice.reporting.webhook.core.NotificationService
import com.aamdigital.aambackendservice.reporting.webhook.core.TriggerWebhookUseCase
import com.aamdigital.aambackendservice.reporting.webhook.core.UriParser
import com.aamdigital.aambackendservice.reporting.webhook.storage.DefaultWebhookStorage
import com.aamdigital.aambackendservice.reporting.webhook.storage.WebhookRepository
import com.aamdigital.aambackendservice.reporting.webhook.storage.WebhookStorage
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient

@Configuration
class ReportingNotificationConfiguration {

    @Bean
    fun defaultAddWebhookSubscription(
        webhookStorage: WebhookStorage,
        reportCalculationStorage: ReportCalculationStorage,
        notificationService: NotificationService,
        createReportCalculationUseCase: CreateReportCalculationUseCase
    ): AddWebhookSubscriptionUseCase =
        DefaultAddWebhookSubscriptionUseCase(
            webhookStorage = webhookStorage,
            reportCalculationStorage = reportCalculationStorage,
            notificationService = notificationService,
            createReportCalculationUseCase = createReportCalculationUseCase
        )

    @Bean
    fun defaultUriParser(): UriParser = DefaultUriParser()

    @Bean
    fun defaultTriggerWebhookUseCase(
        webhookStorage: WebhookStorage,
        @Qualifier("webhook-web-client") restClient: RestClient,
        uriParser: UriParser,
        objectMapper: ObjectMapper
    ): TriggerWebhookUseCase = DefaultTriggerWebhookUseCase(webhookStorage, restClient, uriParser, objectMapper)

    @Bean(name = ["webhook-web-client"])
    fun webhookWebClient(): RestClient {
        val clientBuilder =
            RestClient.builder()

        return clientBuilder.build()
    }

    @Bean
    fun defaultNotificationStorage(
        webhookRepository: WebhookRepository,
        cryptoService: CryptoService,
    ): WebhookStorage = DefaultWebhookStorage(webhookRepository, cryptoService)
}
