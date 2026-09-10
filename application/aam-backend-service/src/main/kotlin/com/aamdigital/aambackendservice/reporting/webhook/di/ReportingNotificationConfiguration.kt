package com.aamdigital.aambackendservice.reporting.webhook.di

import com.aamdigital.aambackendservice.common.couchdb.core.CouchDbClient
import com.aamdigital.aambackendservice.common.crypto.core.CryptoService
import com.aamdigital.aambackendservice.reporting.ConditionalOnReportingEnabled
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
import com.aamdigital.aambackendservice.reporting.webhook.storage.WebhookSubscriptionCache
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
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
         * Two core threads is a modest widening of the single `notification.webhook` consumer
         * (`prefetch: 1`) this replaces; the pool grows to four only once the backlog fills.
         */
        private const val WEBHOOK_EXECUTOR_CORE_POOL_SIZE = 2
        private const val WEBHOOK_EXECUTOR_MAX_POOL_SIZE = 4
        private const val WEBHOOK_EXECUTOR_QUEUE_CAPACITY = 500
        private const val WEBHOOK_EXECUTOR_SHUTDOWN_TIMEOUT_SECONDS = 30
    }

    /**
     * Delivers webhook callbacks off the caller's thread.
     *
     * On shutdown, in-flight and queued callbacks are given
     * [WEBHOOK_EXECUTOR_SHUTDOWN_TIMEOUT_SECONDS] to drain, matching the best-effort flush
     * `ReportCalculationDebouncer` does for its pending triggers.
     */
    @Bean("webhook-notification-executor")
    fun webhookNotificationExecutor(): Executor =
        ThreadPoolTaskExecutor().apply {
            corePoolSize = WEBHOOK_EXECUTOR_CORE_POOL_SIZE
            maxPoolSize = WEBHOOK_EXECUTOR_MAX_POOL_SIZE
            setQueueCapacity(WEBHOOK_EXECUTOR_QUEUE_CAPACITY)
            setThreadNamePrefix("webhook-notification-")
            setWaitForTasksToCompleteOnShutdown(true)
            setAwaitTerminationSeconds(WEBHOOK_EXECUTOR_SHUTDOWN_TIMEOUT_SECONDS)
            initialize()
        }

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
        webhookSubscriptionCache: WebhookSubscriptionCache
    ): WebhookStorage = DefaultWebhookStorage(webhookRepository, cryptoService, webhookSubscriptionCache)

    @Bean
    fun webhookSubscriptionCache(
        webhookRepository: WebhookRepository,
        @Value("\${reporting.webhook-subscription-cache.ttl-millis:1000}") ttlMillis: Long
    ): WebhookSubscriptionCache =
        WebhookSubscriptionCache(
            webhookRepository = webhookRepository,
            ttl = Duration.ofMillis(ttlMillis)
        )

    @Bean
    fun webhookRepository(
        couchDbClient: CouchDbClient,
        objectMapper: ObjectMapper
    ): WebhookRepository = WebhookRepository(couchDbClient, objectMapper)

    @Bean
    fun notificationService(
        webhookStorage: WebhookStorage,
        triggerWebhookUseCase: TriggerWebhookUseCase,
        @Qualifier("webhook-notification-executor") webhookNotificationExecutor: Executor
    ): NotificationService = NotificationService(webhookStorage, triggerWebhookUseCase, webhookNotificationExecutor)
}
