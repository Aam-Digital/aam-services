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
 * [ChangeDetectionProperties.includedDatabases], enriches each change with
 * the current and previous document revision, and hands it to every registered
 * [DocumentChangeHandler].
 *
 * Triggered periodically by [CouchDbChangesPollingJob].
 *
 * Handlers are called synchronously and the sync cursor is saved after each change rather than once
 * per batch, so a crash re-processes at most the one change that was in flight instead of the whole
 * batch. Change handling is expected to be idempotent - notification ids, for instance, are derived
 * from the change that caused them so a replay does not deliver twice.
 */
class CouchDbChangesProcessor(
    private val couchDbClient: CouchDbClient,
    private val documentChangeHandlers: List<DocumentChangeHandler>,
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

    fun checkForChanges() {
        couchDbClient
            .allDatabases()
            .filter { !it.startsWith("_") }
            .filter { it in changeDetectionProperties.includedDatabases }
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
                .orElseGet {
                    // On first run we intentionally skip historic changes and start from "now"
                    // to avoid replaying the full backlog into downstream consumers.
                    SyncEntry(database = database, latestRef = getLatestRef(database))
                }

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

                handleChange(changeEvent)
            }

            // Saved per change, not per batch: a failure part way through then re-processes only
            // this one change instead of everything already handled in this batch.
            syncEntry.latestRef = couchDbChangeResult.seq
            syncRepository.save(syncEntry)
        }

        if (changes.results.isEmpty()) {
            // Nothing to process, but a cursor created just now still has to be persisted:
            // otherwise every tick would re-anchor a fresh database to "now" and silently skip
            // whatever changed between two ticks.
            syncRepository.save(syncEntry)
        }
    }

    /**
     * Gives the change to every handler.
     *
     * A handler that throws is logged and the others still run: change detection feeds several
     * independent modules, and one of them failing on one document must not stop the others or stall
     * the feed. This matches how the queue consumers behaved - they acknowledged the message and
     * logged, leaving recovery to the module itself.
     */
    private fun handleChange(changeEvent: DocumentChangeEvent) {
        documentChangeHandlers.forEach { handler ->
            try {
                handler.handle(changeEvent)
            } catch (ex: Exception) {
                logger.error(
                    "Change handler {} failed for db={}, documentId={}, rev={}: {}",
                    handler.javaClass.simpleName,
                    changeEvent.database,
                    changeEvent.documentId,
                    changeEvent.rev,
                    ex.message,
                    ex
                )
            }
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
