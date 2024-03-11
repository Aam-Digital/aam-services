package com.aamdigital.aambackendservice.notification.di

import com.aamdigital.aambackendservice.crypto.core.CryptoService
import com.aamdigital.aambackendservice.notification.core.DefaultTriggerWebhookUseCase
import com.aamdigital.aambackendservice.notification.core.DefaultUriParser
import com.aamdigital.aambackendservice.notification.core.NotificationStorage
import com.aamdigital.aambackendservice.notification.core.TriggerWebhookUseCase
import com.aamdigital.aambackendservice.notification.core.UriParser
import com.aamdigital.aambackendservice.notification.storage.DefaultNotificationStorage
import com.aamdigital.aambackendservice.notification.storage.WebhookRepository
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient

@Configuration
class NotificationConfiguration {

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
