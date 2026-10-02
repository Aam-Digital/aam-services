package com.aamdigital.aambackendservice.common.changes

import com.aamdigital.aambackendservice.common.cache.LazySnapshot
import com.aamdigital.aambackendservice.common.couchdb.core.CouchDbClient
import com.aamdigital.aambackendservice.common.couchdb.core.getEmptyQueryParams
import com.aamdigital.aambackendservice.common.couchdb.dto.CouchDbChangeResult
import com.aamdigital.aambackendservice.common.couchdb.dto.CouchDbChangesResponse
import com.aamdigital.aambackendservice.common.error.AamErrorCode
import com.aamdigital.aambackendservice.common.error.AamException
import com.aamdigital.aambackendservice.common.error.InvalidArgumentException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.slf4j.LoggerFactory
import java.time.Duration

/**
 * Polls CouchDB `_changes` feeds for the databases allowlisted in
 * [ChangeDetectionProperties.includedDatabases] on behalf of one [DocumentChangeHandler] and hands
 * each change to it.
 *
 * The feed is read with `include_docs`, so the current version of the document arrives with the
 * change itself and costs nothing extra. The previous revision does cost a round trip, so it is
 * loaded only if the handler reads [DocumentChangeEvent.previousVersion]; the list of databases to
 * poll, which is the same answer for every consumer, is read once and shared rather than per
 * consumer per tick.
 *
 * Triggered periodically by [CouchDbChangesPollingJob], separately for every handler: each handler
 * has its own cursor ([SyncEntry.consumer]), so it moves through the feed at its own pace and a
 * handler that is slow or stuck holds back only itself. A poll keeps reading batches while CouchDB
 * reports more changes pending, so a backlog does not wait one polling interval per batch.
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

        /**
         * Batches read back to back in one poll while CouchDB reports more changes pending, so at
         * most `CHANGES_LIMIT * MAX_BATCHES_PER_POLL` changes before the next polling delay.
         */
        internal const val MAX_BATCHES_PER_POLL: Int = 100

        private val POLLED_DATABASES_TTL: Duration = Duration.ofMinutes(1)
    }

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * The databases to poll, shared by every consumer.
     *
     * Each consumer polls on its own thread, so without this each of them would list all databases
     * on every tick to compute the same answer. The set only changes when a database named in the
     * allowlist is created or dropped, so it is held for [POLLED_DATABASES_TTL] rather than read
     * per tick; a database appearing within that window is picked up on a later tick, and its
     * changes are not missed because a new consumer cursor starts from the database's current
     * `update_seq`.
     */
    private val polledDatabases =
        LazySnapshot(ttl = POLLED_DATABASES_TTL) {
            couchDbClient
                .allDatabases()
                .filter { !it.startsWith("_") }
                .filter { it in changeDetectionProperties.includedDatabases }
        }

    enum class CouchDbChangeDetectionError : AamErrorCode {
        COULD_NOT_FETCH_LATEST_REF
    }

    fun checkForChanges(handler: DocumentChangeHandler) {
        polledDatabases.get().forEach { database ->
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

    /**
     * Reads the handler's changes in [database] in batches of [CHANGES_LIMIT], and keeps reading
     * right away while CouchDB reports more `pending`, so that a bulk edit is worked through at
     * the handlers' pace instead of one batch per polling interval. The fixed delay of
     * [CouchDbChangesPollingJob] then only applies once the feed is drained, or after
     * [MAX_BATCHES_PER_POLL] batches, which bounds how long one poll holds its thread.
     */
    private fun fetchChangesForDatabase(
        database: String,
        handler: DocumentChangeHandler
    ) {
        val storedEntry = syncRepository.findByDatabase(database, handler.consumerName).orElse(null)
        var syncEntry =
            storedEntry
                // On first run we intentionally skip historic changes and start from "now"
                // to avoid replaying the full backlog into downstream consumers.
                ?: SyncEntry(
                    database = database,
                    latestRef = getLatestRef(database),
                    consumer = handler.consumerName
                )

        var batches = 0
        var handledChanges = 0
        do {
            val changes = fetchChanges(database, syncEntry.latestRef)
            changes.results.forEach { couchDbChangeResult ->
                handleChangeResult(database, couchDbChangeResult, handler)

                // Saved per change, not per batch: a failure part way through then re-processes
                // only this one change instead of everything already handled in this batch.
                syncEntry = syncEntry.copy(latestRef = couchDbChangeResult.seq)
                syncRepository.save(syncEntry)
            }
            batches++
            handledChanges += changes.results.size
            // An empty batch ends the loop even if `pending` says otherwise: CouchDB's count is an
            // estimate in a cluster, and without results the cursor could not move anyway.
        } while (changes.pending > 0 && changes.results.isNotEmpty() && batches < MAX_BATCHES_PER_POLL)

        if (batches == MAX_BATCHES_PER_POLL) {
            logger.debug(
                "{} handled {} changes of db={} in {} batches, the most one poll reads; any rest follows next poll",
                handler.consumerName,
                handledChanges,
                database,
                batches
            )
        }

        // Every save is a new CouchDB revision, and this runs every few seconds: an idle poll must
        // not write. A cursor created just now still has to be persisted, though: otherwise every
        // tick would re-anchor a fresh database to "now" and silently skip whatever changed between
        // two ticks.
        if (storedEntry == null && handledChanges == 0) {
            syncRepository.save(syncEntry)
        }
    }

    private fun fetchChanges(
        database: String,
        latestRef: String
    ): CouchDbChangesResponse {
        val queryParams = getEmptyQueryParams()

        if (latestRef.isNotEmpty()) {
            queryParams.set("last-event-id", latestRef)
        }

        queryParams.set("limit", CHANGES_LIMIT.toString())
        queryParams.set("include_docs", "true")

        return couchDbClient.getDatabaseChanges(
            database = database,
            queryParams = queryParams
        )
    }

    private fun handleChangeResult(
        database: String,
        couchDbChangeResult: CouchDbChangeResult,
        handler: DocumentChangeHandler
    ) {
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
    }

    /**
     * Gives the change to the handler, and is the one place that decides what a failure means for
     * every handler: log it at ERROR and carry on, so one document a module fails on does not stall
     * that module's feed. The cursor then advances past the change like after any other.
     */
    private fun handleChange(
        changeEvent: DocumentChangeEvent,
        handler: DocumentChangeHandler
    ) {
        logger.trace(
            "handing change to {}: db={}, documentId={}, rev={}, deleted={}",
            handler.consumerName,
            changeEvent.database,
            changeEvent.documentId,
            changeEvent.rev,
            changeEvent.deleted
        )

        try {
            handler.handle(changeEvent)
        } catch (ex: Exception) {
            logger.error(
                "Change handler {} failed for db={}, documentId={}, rev={}",
                handler.consumerName,
                changeEvent.database,
                changeEvent.documentId,
                changeEvent.rev,
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

        return DocumentChangeEvent(
            database = database,
            documentId = documentId,
            rev = rev,
            currentVersion = objectMapper.convertValue(currentDoc, Map::class.java),
            deleted = false,
            previousVersionLoader = { loadPreviousVersion(database, documentId, rev) }
        )
    }

    /**
     * Reads the revision before [rev], as a map. Called only if a handler reads
     * [DocumentChangeEvent.previousVersion].
     *
     * A missing previous revision is normal rather than exceptional - CouchDB compacts old
     * revisions away - so a failure here yields an empty map instead of failing the change.
     */
    private fun loadPreviousVersion(
        database: String,
        documentId: String,
        rev: String
    ): Map<*, *> {
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
                logger.debug("No previous revision for db={}, documentId={}, rev={}", database, documentId, rev, ex)
                objectMapper.createObjectNode()
            }

        return objectMapper.convertValue(previousDoc, Map::class.java)
    }
}
