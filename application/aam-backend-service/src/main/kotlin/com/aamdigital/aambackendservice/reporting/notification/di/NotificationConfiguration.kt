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
import com.aamdigital.aambackendservice.reporting.report.calculation.core.CreateReportCalculationUseCase
import com.aamdigital.aambackendservice.reporting.report.core.ReportingStorage
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient

@Configuration
class NotificationConfiguration {

    @Bean
    fun defaultAddWebhookSubscription(
        notificationStorage: NotificationStorage,
        reportingStorage: ReportingStorage,
        notificationService: NotificationService,
        createReportCalculationUseCase: CreateReportCalculationUseCase
    ): AddWebhookSubscriptionUseCase =
        DefaultAddWebhookSubscriptionUseCase(
            notificationStorage = notificationStorage,
            reportingStorage = reportingStorage,
            notificationService = notificationService,
            createReportCalculationUseCase = createReportCalculationUseCase
        )

    @Bean
    fun defaultUriParser(): UriParser = DefaultUriParser()

    @Bean
    fun defaultTriggerWebhookUseCase(
        notificationStorage: NotificationStorage,
        @Qualifier("webhook-web-client") webClient: WebClient,
        uriParser: UriParser,
    ): TriggerWebhookUseCase = DefaultTriggerWebhookUseCase(notificationStorage, webClient, uriParser)

    @Bean(name = ["webhook-web-client"])
    fun webhookWebClient(): WebClient {
        val clientBuilder =
            WebClient.builder()

        return clientBuilder.clientConnector(ReactorClientHttpConnector(HttpClient.create())).build()
    }

    @Bean
    fun defaultNotificationStorage(
        webhookRepository: WebhookRepository,
        cryptoService: CryptoService,
    ): NotificationStorage = DefaultNotificationStorage(webhookRepository, cryptoService)
}
