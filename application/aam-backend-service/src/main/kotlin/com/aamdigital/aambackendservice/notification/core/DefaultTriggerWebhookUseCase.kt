package com.aamdigital.aambackendservice.notification.core

import com.aamdigital.aambackendservice.couchdb.core.getEmptyQueryParams
import com.aamdigital.aambackendservice.domain.DomainReference
import com.aamdigital.aambackendservice.notification.core.event.NotificationEvent
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import java.net.URI

class DefaultTriggerWebhookUseCase(
    private val notificationStorage: NotificationStorage,
    private val webClient: WebClient,
    private val uriParser: UriParser,
) : TriggerWebhookUseCase {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun trigger(notificationEvent: NotificationEvent): Mono<Unit> {
        return notificationStorage.fetchWebhook(
            webhookRef = DomainReference(notificationEvent.webhookId)
        )
            .flatMap { webhook ->
                val queryParams = getEmptyQueryParams()
                queryParams.set("calculation_id", notificationEvent.calculationId)

                val uri = URI(
                    uriParser.replacePlaceholder(
                        webhook.target.url,
                        mapOf(
                            Pair("reportId", notificationEvent.reportId)
                        )
                    )
                )

                webClient
                    .method(HttpMethod.valueOf(webhook.target.method))
                    .uri {
                        it.scheme(uri.scheme)
                        it.host(uri.host)
                        it.path(uri.path)
                        it.queryParams(queryParams)
                        it.build()
                    }
                    .headers {
                        it.set(HttpHeaders.AUTHORIZATION, "Token ${webhook.authentication.secret}")
                    }
                    .accept(MediaType.APPLICATION_JSON)
                    .exchangeToMono { response ->
                        response.bodyToMono(String::class.java)
                    }
                    .map {
                        logger.trace("[DefaultTriggerWebhookUseCase] Webhook trigger completed. Response: {}", it)
                    }
            }
    }
}