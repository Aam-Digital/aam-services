package com.aamdigital.aambackendservice.common.changes.core

import com.aamdigital.aambackendservice.common.changes.di.ChangesQueueConfiguration.Companion.DOCUMENT_CHANGES_EXCHANGE
import com.aamdigital.aambackendservice.common.changes.domain.DatabaseChangeEvent
import com.aamdigital.aambackendservice.common.changes.domain.DocumentChangeEvent
import com.aamdigital.aambackendservice.common.error.AamException
import com.aamdigital.aambackendservice.common.couchdb.core.CouchDbClient
import com.aamdigital.aambackendservice.common.couchdb.core.getEmptyQueryParams
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.slf4j.LoggerFactory
import kotlin.jvm.optionals.getOrDefault

/**
 * Use case is called if a change on any database document is detected.
 */
class DefaultCreateDocumentChangeUseCase(
    private val couchDbClient: CouchDbClient,
    private val objectMapper: ObjectMapper,
    private val documentChangeEventPublisher: ChangeEventPublisher,
) : CreateDocumentChangeUseCase {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun createEvent(event: DatabaseChangeEvent) {
        val queryParams = getEmptyQueryParams()
        queryParams.set("rev", event.rev)

        val currentDoc = couchDbClient.getDatabaseDocument(
            database = event.database,
            documentId = event.documentId,
            queryParams = queryParams,
            kClass = ObjectNode::class
        )

        if (event.rev.isNullOrBlank()) {
            return
        }

        val previousDoc: ObjectNode = try {
            couchDbClient.getPreviousDocRev(
                database = event.database,
                documentId = event.documentId,
                rev = event.rev,
                kClass = ObjectNode::class
            ).getOrDefault(
                objectMapper.createObjectNode()
            )
        } catch (ex: AamException) {
            logger.debug(ex.message, ex)
            objectMapper.createObjectNode()
        }

        val changeEvent = if (currentDoc.has("_deleted")
            && currentDoc.get("_deleted").isBoolean
            && currentDoc.get("_deleted").booleanValue()
        ) {
            DocumentChangeEvent(
                database = event.database,
                documentId = event.documentId,
                rev = event.rev,
                currentVersion = emptyMap<String, Any>(),
                previousVersion = emptyMap<String, Any>(),
                deleted = event.deleted
            )
        } else {
            DocumentChangeEvent(
                database = event.database,
                documentId = event.documentId,
                rev = event.rev,
                currentVersion = objectMapper.convertValue(currentDoc, Map::class.java),
                previousVersion = objectMapper.convertValue(previousDoc, Map::class.java),
                deleted = event.deleted
            )
        }
        documentChangeEventPublisher.publish(DOCUMENT_CHANGES_EXCHANGE, changeEvent)
    }
}
