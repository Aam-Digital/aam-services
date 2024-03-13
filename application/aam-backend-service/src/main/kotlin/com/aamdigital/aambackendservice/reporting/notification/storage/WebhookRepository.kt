package com.aamdigital.aambackendservice.reporting.notification.storage

import com.aamdigital.aambackendservice.couchdb.core.CouchDbStorage
import com.aamdigital.aambackendservice.couchdb.core.getQueryParamsAllDocs
import com.aamdigital.aambackendservice.couchdb.dto.DocSuccess
import com.aamdigital.aambackendservice.domain.DomainReference
import com.aamdigital.aambackendservice.error.InternalServerException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.util.LinkedMultiValueMap
import reactor.core.publisher.Mono

@Service
class WebhookRepository(
    private val couchDbStorage: CouchDbStorage,
    private val objectMapper: ObjectMapper,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val WEBHOOK_DATABASE = "notification-webhook"
    }

    fun fetchAllWebhooks(): Mono<List<WebhookEntity>> {
        return couchDbStorage
            .getDatabaseDocument(
                database = WEBHOOK_DATABASE,
                documentId = "_all_docs",
                queryParams = getQueryParamsAllDocs("Webhook"),
                kClass = ObjectNode::class
            ).map { objectNode ->
                val data =
                    (objectMapper.convertValue(objectNode, Map::class.java)["rows"] as Iterable<*>)
                        .map { entry ->
                            if (entry is LinkedHashMap<*, *>) {
                                objectMapper.convertValue(entry["doc"], WebhookEntity::class.java)
                            } else {
                                throw InternalServerException("Invalid response")
                            }
                        }

                data
            }
    }

    fun fetchWebhook(webhookRef: DomainReference): Mono<WebhookEntity> {
        return couchDbStorage
            .getDatabaseDocument(
                database = WEBHOOK_DATABASE,
                documentId = webhookRef.id,
                queryParams = LinkedMultiValueMap(),
                kClass = WebhookEntity::class
            )
    }

    fun storeWebhook(webhook: WebhookEntity): Mono<DocSuccess> {
        return couchDbStorage
            .putDatabaseDocument(
                database = WEBHOOK_DATABASE,
                documentId = webhook.id,
                body = webhook,
            )
    }
}
