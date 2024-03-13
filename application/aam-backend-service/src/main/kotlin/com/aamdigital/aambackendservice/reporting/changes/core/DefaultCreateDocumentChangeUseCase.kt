package com.aamdigital.aambackendservice.reporting.changes.core

import com.aamdigital.aambackendservice.couchdb.core.CouchDbStorage
import com.aamdigital.aambackendservice.couchdb.core.getEmptyQueryParams
import com.aamdigital.aambackendservice.domain.event.DocumentChangeEvent
import com.aamdigital.aambackendservice.reporting.changes.core.event.DatabaseChangeEvent
import com.aamdigital.aambackendservice.reporting.changes.di.ChangesQueueConfiguration.Companion.DOCUMENT_CHANGES_EXCHANGE
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.slf4j.LoggerFactory
import reactor.core.publisher.Mono

/**
 * Use case is called if a change on any database document is detected.
 */
class DefaultCreateDocumentChangeUseCase(
    private val couchDbStorage: CouchDbStorage,
    private val objectMapper: ObjectMapper,
    private val documentChangeEventPublisher: ChangeEventPublisher,
) : CreateDocumentChangeUseCase {
    val logger = LoggerFactory.getLogger(javaClass)

    override fun createEvent(event: DatabaseChangeEvent): Mono<Unit> {
        val queryParams = getEmptyQueryParams()
        queryParams.set("rev", event.rev)

        return couchDbStorage.getDatabaseDocument(
            database = event.database,
            documentId = event.documentId,
            queryParams = queryParams,
            kClass = ObjectNode::class
        ).zipWith(
            if (event.rev.isNullOrBlank()) {
                return Mono.empty()
            } else {
                couchDbStorage.getPreviousDocRev(
                    database = event.database,
                    documentId = event.documentId,
                    rev = event.rev,
                    kClass = ObjectNode::class
                ).defaultIfEmpty(
                    objectMapper.createObjectNode()
                )
            }
        ).map {
            val currentDoc = it.t1
            val previousDoc = it.t2

            if (currentDoc.has("_deleted")
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
            }

            DocumentChangeEvent(
                database = event.database,
                documentId = event.documentId,
                rev = event.rev,
                currentVersion = objectMapper.convertValue(currentDoc, Map::class.java),
                previousVersion = objectMapper.convertValue(previousDoc, Map::class.java),
                deleted = event.deleted
            )
        }.map {
            logger.debug("[{}]: send event: {}", DOCUMENT_CHANGES_EXCHANGE, it)
            documentChangeEventPublisher.publish(DOCUMENT_CHANGES_EXCHANGE, it)
        }
    }
}
