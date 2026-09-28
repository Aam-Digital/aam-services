package com.aamdigital.aambackendservice.reporting.webhook.di

import com.aamdigital.aambackendservice.common.couchdb.core.CouchDbClient
import com.aamdigital.aambackendservice.common.crypto.core.CryptoService
import com.aamdigital.aambackendservice.common.execution.BoundedTaskRunner
import com.aamdigital.aambackendservice.reporting.ConditionalOnReportingEnabled
import com.aamdigital.aambackendservice.reporting.reportcalculation.core.CreateReportCalculationUseCase
import com.aamdigital.aambackendservice.reporting.reportcalculation.core.ReportCalculationStorage
import com.aamdigital.aambackendservice.reporting.webhook.core.AddWebhookSubscriptionUseCase
import com.aamdigital.aambackendservice.reporting.webhook.core.DefaultAddWebhookSubscriptionUseCase
import com.aamdigital.aambackendservice.reporting.webhook.core.DefaultTriggerWebhookUseCase
import com.aamdigital.aambackendservice.reporting.webhook.core.DefaultUriParser
import com.aamdigital.aambackendservice.reporting.webhook.core.TriggerWebhookUseCase
import com.aamdigital.aambackendservice.reporting.webhook.core.UriParser
import com.aamdigital.aambackendservice.reporting.webhook.core.WebhookTriggerService
import com.aamdigital.aambackendservice.reporting.webhook.storage.DefaultWebhookStorage
import com.aamdigital.aambackendservice.reporting.webhook.storage.WebhookRepository
import com.aamdigital.aambackendservice.reporting.webhook.storage.WebhookStorage
import com.aamdigital.aambackendservice.reporting.webhook.storage.WebhookSubscriptionCache
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient
import java.time.Duration
import java.util.concurrent.Executor

@Configuration
@ConditionalOnReportingEnabled
class ReportingNotificationConfiguration {
    companion object {
        /**
         * Webhook delivery is one outbound HTTP call per subscribed report per new calculation
         * result: low volume, but each call is external and has no client timeout yet, so the pool
         * stays small and the backlog is bounded rather than unbounded like the queue it replaces.
         *
         * Two at a time is a modest widening of the single `notification.webhook` consumer
         * (`prefetch: 1`) this replaces.
         */
        private const val WEBHOOK_DELIVERY_CONCURRENCY = 2
        private const val WEBHOOK_DELIVERY_BACKLOG = 500
        private val WEBHOOK_DELIVERY_SHUTDOWN_TIMEOUT: Duration = Duration.ofSeconds(30)
    }

    /**
     * Delivers webhook callbacks off the caller's thread.
     *
     * On shutdown, in-flight and queued callbacks are given [WEBHOOK_DELIVERY_SHUTDOWN_TIMEOUT] to
     * drain, matching the best-effort flush `ReportCalculationDebouncer` does for its pending
     * triggers.
     */
    @Bean("webhook-delivery-executor")
    fun webhookDeliveryExecutor(): Executor =
        BoundedTaskRunner.threadPool(
            name = "webhook-delivery",
            concurrency = WEBHOOK_DELIVERY_CONCURRENCY,
            backlog = WEBHOOK_DELIVERY_BACKLOG,
            shutdownTimeout = WEBHOOK_DELIVERY_SHUTDOWN_TIMEOUT
        )

    @Bean
    fun defaultAddWebhookSubscription(
        webhookStorage: WebhookStorage,
        reportCalculationStorage: ReportCalculationStorage,
        webhookTriggerService: WebhookTriggerService,
        createReportCalculationUseCase: CreateReportCalculationUseCase
    ): AddWebhookSubscriptionUseCase =
        DefaultAddWebhookSubscriptionUseCase(
            webhookStorage = webhookStorage,
            reportCalculationStorage = reportCalculationStorage,
            webhookTriggerService = webhookTriggerService,
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
        webhookSubscriptionCache: WebhookSubscriptionCache
    ): WebhookStorage = DefaultWebhookStorage(webhookRepository, cryptoService, webhookSubscriptionCache)

    @Bean
    fun webhookSubscriptionCache(
        webhookRepository: WebhookRepository,
        @Value("\${reporting.webhook-subscription-cache.ttl-millis:3600000}") ttlMillis: Long
    ): WebhookSubscriptionCache =
        WebhookSubscriptionCache(
            webhookRepository = webhookRepository,
            ttl = Duration.ofMillis(ttlMillis)
        )

    @Bean
    fun webhookRepository(couchDbClient: CouchDbClient): WebhookRepository = WebhookRepository(couchDbClient)

    @Bean
    fun webhookTriggerService(
        webhookStorage: WebhookStorage,
        triggerWebhookUseCase: TriggerWebhookUseCase,
        @Qualifier("webhook-delivery-executor") webhookDeliveryExecutor: Executor
    ): WebhookTriggerService =
        WebhookTriggerService(
            webhookStorage = webhookStorage,
            triggerWebhookUseCase = triggerWebhookUseCase,
            webhookDeliveryRunner = BoundedTaskRunner("webhook-delivery", webhookDeliveryExecutor)
        )
}
