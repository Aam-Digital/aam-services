package com.aamdigital.aambackendservice.common.changes

import com.aamdigital.aambackendservice.common.couchdb.core.CouchDbClient
import com.aamdigital.aambackendservice.common.couchdb.core.getEmptyQueryParams
import com.aamdigital.aambackendservice.common.error.AamErrorCode
import com.aamdigital.aambackendservice.common.error.AamException
import com.aamdigital.aambackendservice.common.error.InvalidArgumentException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.slf4j.LoggerFactory
import kotlin.jvm.optionals.getOrDefault

/**
 * Polls CouchDB `_changes` feeds for all databases, enriches each change with
 * the current and previous document revision, and publishes a [DocumentChangeEvent]
 * to the RabbitMQ fanout exchange.
 *
 * Triggered periodically by [CouchDbChangeDetectionJob].
 */
class CouchDbDatabaseChangeDetection(
    private val couchDbClient: CouchDbClient,
    private val documentChangeEventPublisher: ChangeEventPublisher,
    private val syncRepository: SyncRepository,
    private val objectMapper: ObjectMapper,
) {
    companion object {
        private const val CHANGES_LIMIT: Int = 100
    }

    private val logger = LoggerFactory.getLogger(javaClass)

    enum class CouchDbChangeDetectionError : AamErrorCode {
        COULD_NOT_FETCH_LATEST_REF
    }

    fun checkForChanges() {
        couchDbClient
            .allDatabases()
            .filter { !it.startsWith("_") }
            .forEach { database ->
                fetchChangesForDatabase(database)
            }
    }

    private fun getLatestRef(database: String): String =
        try {
            couchDbClient
                .getDatabaseDocument(
                    database = database,
                    documentId = "",
                    kClass = ObjectNode::class
                ).get("update_seq")
                .textValue()
        } catch (ex: Exception) {
            throw InvalidArgumentException(
                message = "Could not fetch latest update_seq",
                cause = ex,
                code = CouchDbChangeDetectionError.COULD_NOT_FETCH_LATEST_REF
            )
        }

    private fun fetchChangesForDatabase(database: String) {
        val syncEntry =
            syncRepository
                .findByDatabase(database)
                .getOrDefault(SyncEntry(database = database, latestRef = getLatestRef(database)))

        val queryParams = getEmptyQueryParams()

        if (syncEntry.latestRef.isNotEmpty()) {
            queryParams.set("last-event-id", syncEntry.latestRef)
        }

        queryParams.set("limit", CHANGES_LIMIT.toString())
        queryParams.set("include_docs", "true")

        val changes =
            couchDbClient.getDatabaseChanges(
                database = database,
                queryParams = queryParams
            )

        var latestSeq = syncEntry.latestRef

        changes.results.forEach { couchDbChangeResult ->
            val rev = couchDbChangeResult.doc?.get("_rev")?.textValue()

            if (!couchDbChangeResult.id.startsWith("_design") && rev != null) {
                val changeEvent = enrichChange(
                    database = database,
                    documentId = couchDbChangeResult.id,
                    rev = rev,
                    deleted = couchDbChangeResult.deleted == true,
                    currentDoc = couchDbChangeResult.doc,
                )

                documentChangeEventPublisher.publish(
                    ChangesQueueConfiguration.DOCUMENT_CHANGES_EXCHANGE, changeEvent
                )
            }

            latestSeq = couchDbChangeResult.seq
        }

        syncEntry.latestRef = latestSeq
        syncRepository.save(syncEntry)
    }

    private fun enrichChange(
        database: String,
        documentId: String,
        rev: String,
        deleted: Boolean,
        currentDoc: ObjectNode?,
    ): DocumentChangeEvent {
        val isDeleted = deleted ||
            (currentDoc?.has("_deleted") == true &&
                currentDoc.get("_deleted").isBoolean &&
                currentDoc.get("_deleted").booleanValue())

        if (isDeleted) {
            return DocumentChangeEvent(
                database = database,
                documentId = documentId,
                rev = rev,
                currentVersion = emptyMap<String, Any>(),
                previousVersion = emptyMap<String, Any>(),
                deleted = true
            )
        }

        val previousDoc: ObjectNode =
            try {
                couchDbClient
                    .getPreviousDocumentRevision(
                        database = database,
                        documentId = documentId,
                        rev = rev,
                        kClass = ObjectNode::class
                    ).getOrDefault(
                        objectMapper.createObjectNode()
                    )
            } catch (ex: AamException) {
                logger.debug(ex.message, ex)
                objectMapper.createObjectNode()
            }

        return DocumentChangeEvent(
            database = database,
            documentId = documentId,
            rev = rev,
            currentVersion = objectMapper.convertValue(currentDoc, Map::class.java),
            previousVersion = objectMapper.convertValue(previousDoc, Map::class.java),
            deleted = false
        )
    }
}
