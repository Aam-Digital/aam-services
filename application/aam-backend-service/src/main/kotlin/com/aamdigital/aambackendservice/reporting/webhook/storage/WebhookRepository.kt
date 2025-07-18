package com.aamdigital.aambackendservice.reporting.webhook.storage

import com.aamdigital.aambackendservice.common.couchdb.core.CouchDbClient
import com.aamdigital.aambackendservice.common.couchdb.core.getQueryParamsAllDocs
import com.aamdigital.aambackendservice.common.couchdb.dto.DocSuccess
import com.aamdigital.aambackendservice.common.domain.DomainReference
import com.aamdigital.aambackendservice.common.error.AamErrorCode
import com.aamdigital.aambackendservice.common.error.InternalServerException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.springframework.stereotype.Service
import org.springframework.util.LinkedMultiValueMap

@Service
class WebhookRepository(
    private val couchDbClient: CouchDbClient,
    private val objectMapper: ObjectMapper,
) {

    enum class WebhookRepositoryErrorCode : AamErrorCode {
        INVALID_RESPONSE
    }

    companion object {
        private const val WEBHOOK_DATABASE = "notification-webhook"
    }

    fun fetchAllWebhooks(): List<WebhookEntity> {
        val objectNode = couchDbClient
            .getDatabaseDocument(
                database = WEBHOOK_DATABASE,
                documentId = "_all_docs",
                queryParams = getQueryParamsAllDocs("Webhook"),
                kClass = ObjectNode::class
            )

        val data = objectMapper.convertValue(objectNode, Map::class.java)
        val rows = (data["rows"] as Iterable<*>)
            .map { entry ->
                if (entry is LinkedHashMap<*, *>) {
                    objectMapper.convertValue(entry["doc"], WebhookEntity::class.java)
                } else {
                    throw InternalServerException(
                        message = "Invalid response",
                        code = WebhookRepositoryErrorCode.INVALID_RESPONSE
                    )
                }
            }
        return rows
    }

    fun fetchWebhook(webhookRef: DomainReference): WebhookEntity {
        return couchDbClient
            .getDatabaseDocument(
                database = WEBHOOK_DATABASE,
                documentId = webhookRef.id,
                queryParams = LinkedMultiValueMap(),
                kClass = WebhookEntity::class
            )
    }

    fun storeWebhook(webhook: WebhookEntity): DocSuccess {
        return couchDbClient
            .putDatabaseDocument(
                database = WEBHOOK_DATABASE,
                documentId = webhook.id,
                body = webhook,
            )
    }
}
