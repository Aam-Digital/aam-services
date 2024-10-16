package com.aamdigital.aambackendservice.reporting.notification.core

import com.aamdigital.aambackendservice.domain.DomainReference
import com.aamdigital.aambackendservice.reporting.domain.event.NotificationEvent
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.web.client.RestClient
import java.net.URI

/**
 * Calls a configured (external) webhook
 */
class DefaultTriggerWebhookUseCase(
    private val notificationStorage: NotificationStorage,
    private val httpClient: RestClient,
    private val uriParser: UriParser,
) : TriggerWebhookUseCase {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun trigger(notificationEvent: NotificationEvent) {
        val webhook = notificationStorage.fetchWebhook(
            webhookRef = DomainReference(notificationEvent.webhookId)
        )

        val uri = URI(
            uriParser.replacePlaceholder(
                webhook.target.url,
                mapOf(
                    Pair("reportId", notificationEvent.reportId)
                )
            )
        )

        val response = httpClient
            .method(HttpMethod.valueOf(webhook.target.method))
            .uri {
                it.scheme(uri.scheme)
                it.host(uri.host)
                it.path(uri.path)
                it.build()
            }
            .headers {
                it.set(HttpHeaders.AUTHORIZATION, "Token ${webhook.authentication.secret}")
            }
            .body(
                mapOf(
                    Pair("calculation_id", notificationEvent.calculationId)
                )
            )
            .accept(MediaType.APPLICATION_JSON)
            .retrieve()
            .body(String::class.java)

        logger.debug(
            "[DefaultTriggerWebhookUseCase] Webhook trigger completed for Webhook:" +
                    " {} Report: {} Calculation: {} - Response: {}",
            notificationEvent.webhookId,
            notificationEvent.reportId,
            notificationEvent.calculationId,
            response
        )
    }
}
