package com.aamdigital.aambackendservice.common.changes

import com.aamdigital.aambackendservice.common.couchdb.core.CouchDbClient
import com.aamdigital.aambackendservice.common.couchdb.core.getEmptyQueryParams
import com.aamdigital.aambackendservice.common.error.AamErrorCode
import com.aamdigital.aambackendservice.common.error.AamException
import com.aamdigital.aambackendservice.common.error.InvalidArgumentException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.slf4j.LoggerFactory

/**
 * Polls CouchDB `_changes` feeds for the databases allowlisted in
 * [ChangeDetectionProperties.includedDatabases] on behalf of one [DocumentChangeHandler], enriches
 * each change with the current and previous document revision, and hands it to that handler.
 *
 * Triggered periodically by [CouchDbChangesPollingJob], separately for every handler: each handler
 * has its own cursor ([SyncEntry.consumer]), so it moves through the feed at its own pace and a
 * handler that is slow or stuck holds back only itself.
 *
 * The handler is called synchronously and its cursor is saved after each change rather than once
 * per batch, so a crash re-processes at most the one change that was in flight instead of the whole
 * batch. Change handling is expected to be idempotent - notification ids, for instance, are derived
 * from the change that caused them so a replay does not create a second in-app notification.
 */
class CouchDbChangesProcessor(
    private val couchDbClient: CouchDbClient,
    private val syncRepository: SyncRepository,
    private val objectMapper: ObjectMapper,
    private val changeDetectionProperties: ChangeDetectionProperties,
) {
    companion object {
        private const val CHANGES_LIMIT: Int = 100
    }

    private val logger = LoggerFactory.getLogger(javaClass)

    enum class CouchDbChangeDetectionError : AamErrorCode {
        COULD_NOT_FETCH_LATEST_REF
    }

    fun checkForChanges(handler: DocumentChangeHandler) {
        couchDbClient
            .allDatabases()
            .filter { !it.startsWith("_") }
            .filter { it in changeDetectionProperties.includedDatabases }
            .forEach { database ->
                fetchChangesForDatabase(database, handler)
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

    private fun fetchChangesForDatabase(
        database: String,
        handler: DocumentChangeHandler
    ) {
        val storedEntry = syncRepository.findByDatabase(database, handler.consumerName).orElse(null)
        val syncEntry =
            storedEntry
                // On first run we intentionally skip historic changes and start from "now"
                // to avoid replaying the full backlog into downstream consumers.
                ?: SyncEntry(
                    database = database,
                    latestRef = getLatestRef(database),
                    consumer = handler.consumerName
                )

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

        var cursor = syncEntry

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

                handleChange(changeEvent, handler)
            }

            // Saved per change, not per batch: a failure part way through then re-processes only
            // this one change instead of everything already handled in this batch.
            cursor = cursor.copy(latestRef = couchDbChangeResult.seq)
            syncRepository.save(cursor)
        }

        // Every save is a new CouchDB revision, and this runs every few seconds: an idle poll must
        // not write. A cursor created just now still has to be persisted, though: otherwise every
        // tick would re-anchor a fresh database to "now" and silently skip whatever changed between
        // two ticks.
        if (storedEntry == null && changes.results.isEmpty()) {
            syncRepository.save(syncEntry)
        }
    }

    /**
     * Gives the change to the handler.
     *
     * The shared disposition - log and carry on, so one document a module fails on does not stall
     * that module's feed - belongs to [AbstractDocumentChangeHandler] and is applied before an
     * exception ever gets here. This catch is only a backstop, for a handler that implements
     * [DocumentChangeHandler] directly or overrides its error handling with something that throws
     * in turn; either way its cursor must still advance past the change.
     */
    private fun handleChange(
        changeEvent: DocumentChangeEvent,
        handler: DocumentChangeHandler
    ) {
        try {
            handler.handle(changeEvent)
        } catch (ex: Exception) {
            logger.error(
                "Change handler {} failed for db={}, documentId={}, rev={}: {}",
                handler.consumerName,
                changeEvent.database,
                changeEvent.documentId,
                changeEvent.rev,
                ex.message,
                ex
            )
        }
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
                    ).orElseGet {
                        objectMapper.createObjectNode()
                    }
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
