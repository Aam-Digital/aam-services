package com.aamdigital.aambackendservice.reporting.webhook.core

import com.aamdigital.aambackendservice.common.domain.DomainReference
import com.aamdigital.aambackendservice.reporting.webhook.WebhookEvent
import com.aamdigital.aambackendservice.reporting.webhook.storage.WebhookStorage
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.RestClient
import java.net.URI

/**
 * Thrown when the webhook receiver itself responds with an error status, so the RabbitMQ listener
 * failure log (and the Sentry event it produces) reads as "the receiver rejected our callback"
 * instead of a bare, unattributed `HttpClientErrorException` message.
 */
class WebhookCallbackRejectedException(message: String) : RuntimeException(message)

/**
 * Calls a configured (external) webhook
 */
class DefaultTriggerWebhookUseCase(
    private val webhookStorage: WebhookStorage,
    private val httpClient: RestClient,
    private val uriParser: UriParser,
    private val objectMapper: ObjectMapper
) : TriggerWebhookUseCase {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun trigger(webhookEvent: WebhookEvent) {
        val webhook =
            webhookStorage.fetchWebhook(
                webhookRef = DomainReference(webhookEvent.webhookId)
            )

        val uri =
            URI(
                uriParser.replacePlaceholder(
                    webhook.target.url,
                    mapOf(
                        Pair("reportId", webhookEvent.reportId)
                    )
                )
            )

        logger.debug(
            "[DefaultTriggerWebhookUseCase] Trying to trigger Webhook" +
                " {} to {} Report: {} Calculation: {} ",
            webhookEvent.webhookId,
            uri.toString(),
            webhookEvent.reportId,
            webhookEvent.calculationId
        )
        val response =
            try {
                httpClient
                    .method(HttpMethod.valueOf(webhook.target.method))
                    .uri {
                        it.scheme(uri.scheme)
                        it.host(uri.host)
                        it.path(uri.path)
                        it.build()
                    }.headers {
                        it.set(HttpHeaders.AUTHORIZATION, "Token ${webhook.authentication.secret}")
                    }.contentType(MediaType.APPLICATION_JSON)
                    .body(
                        objectMapper.writeValueAsString(
                            hashMapOf(
                                "calculation_id" to webhookEvent.calculationId
                            )
                        )
                    ).accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(String::class.java)
            } catch (ex: HttpStatusCodeException) {
                throw WebhookCallbackRejectedException(
                    "Webhook receiver ${uri.host} rejected our callback for webhook" +
                        " ${webhookEvent.webhookId}: ${ex.message}"
                )
            }

        logger.debug(
            "[DefaultTriggerWebhookUseCase] Webhook trigger completed for Webhook:" +
                " {} Report: {} Calculation: {} - Response: {}",
            webhookEvent.webhookId,
            webhookEvent.reportId,
            webhookEvent.calculationId,
            response
        )
    }
}
