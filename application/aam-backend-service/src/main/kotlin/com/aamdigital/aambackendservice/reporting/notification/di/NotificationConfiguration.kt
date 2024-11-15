package com.aamdigital.aambackendservice.reporting.notification.di

import com.aamdigital.aambackendservice.crypto.core.CryptoService
import com.aamdigital.aambackendservice.reporting.notification.core.AddWebhookSubscriptionUseCase
import com.aamdigital.aambackendservice.reporting.notification.core.DefaultAddWebhookSubscriptionUseCase
import com.aamdigital.aambackendservice.reporting.notification.core.DefaultTriggerWebhookUseCase
import com.aamdigital.aambackendservice.reporting.notification.core.DefaultUriParser
import com.aamdigital.aambackendservice.reporting.notification.core.NotificationService
import com.aamdigital.aambackendservice.reporting.notification.core.NotificationStorage
import com.aamdigital.aambackendservice.reporting.notification.core.TriggerWebhookUseCase
import com.aamdigital.aambackendservice.reporting.notification.core.UriParser
import com.aamdigital.aambackendservice.reporting.notification.storage.DefaultNotificationStorage
import com.aamdigital.aambackendservice.reporting.notification.storage.WebhookRepository
import com.aamdigital.aambackendservice.reporting.reportcalculation.core.CreateReportCalculationUseCase
import com.aamdigital.aambackendservice.reporting.reportcalculation.core.ReportCalculationStorage
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient

@Configuration
class NotificationConfiguration {

    @Bean
    fun defaultAddWebhookSubscription(
        notificationStorage: NotificationStorage,
        reportCalculationStorage: ReportCalculationStorage,
        notificationService: NotificationService,
        createReportCalculationUseCase: CreateReportCalculationUseCase
    ): AddWebhookSubscriptionUseCase =
        DefaultAddWebhookSubscriptionUseCase(
            notificationStorage = notificationStorage,
            reportCalculationStorage = reportCalculationStorage,
            notificationService = notificationService,
            createReportCalculationUseCase = createReportCalculationUseCase
        )

    @Bean
    fun defaultUriParser(): UriParser = DefaultUriParser()

    @Bean
    fun defaultTriggerWebhookUseCase(
        notificationStorage: NotificationStorage,
        @Qualifier("webhook-web-client") restClient: RestClient,
        uriParser: UriParser,
        objectMapper: ObjectMapper
    ): TriggerWebhookUseCase = DefaultTriggerWebhookUseCase(notificationStorage, restClient, uriParser, objectMapper)

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
    ): NotificationStorage = DefaultNotificationStorage(webhookRepository, cryptoService)
}
