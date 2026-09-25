package com.aamdigital.aambackendservice.reporting.webhook.storage

import com.aamdigital.aambackendservice.common.couchdb.core.CouchDbClient
import com.aamdigital.aambackendservice.common.couchdb.dto.DocSuccess
import com.aamdigital.aambackendservice.common.domain.DomainReference
import org.springframework.util.LinkedMultiValueMap

class WebhookRepository(
    private val couchDbClient: CouchDbClient
) {
    companion object {
        private const val WEBHOOK_DATABASE = "notification-webhook"
    }

    fun fetchAllWebhooks(): List<WebhookEntity> =
        couchDbClient.getDatabaseDocumentsByPrefix(
            database = WEBHOOK_DATABASE,
            prefix = "Webhook",
            kClass = WebhookEntity::class
        )

    fun fetchWebhook(webhookRef: DomainReference): WebhookEntity =
        couchDbClient
            .getDatabaseDocument(
                database = WEBHOOK_DATABASE,
                documentId = webhookRef.id,
                queryParams = LinkedMultiValueMap(),
                kClass = WebhookEntity::class
            )

    fun storeWebhook(webhook: WebhookEntity): DocSuccess =
        couchDbClient
            .putDatabaseDocument(
                database = WEBHOOK_DATABASE,
                documentId = webhook.id,
                body = webhook
            )
}
