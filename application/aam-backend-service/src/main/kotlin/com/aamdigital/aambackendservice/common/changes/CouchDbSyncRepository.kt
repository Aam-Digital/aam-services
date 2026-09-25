package com.aamdigital.aambackendservice.common.changes

import com.aamdigital.aambackendservice.common.couchdb.core.BACKEND_STATE_DATABASE
import com.aamdigital.aambackendservice.common.couchdb.core.CouchDbClient
import com.aamdigital.aambackendservice.common.error.AamErrorCode
import com.aamdigital.aambackendservice.common.error.ExternalSystemException
import com.aamdigital.aambackendservice.common.error.NotFoundException
import com.fasterxml.jackson.annotation.JsonProperty
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap

/** A [SyncEntry] document as read back, with the revision the next write has to name. */
internal data class SyncEntryDocument(
    val database: String,
    val latestRef: String,
    val consumer: String? = null,
    @JsonProperty("_rev") val rev: String
)

/**
 * [SyncRepository] backed by one CouchDB document per watched database and change consumer,
 * `SyncEntry:<database>:<consumer>`. The shared cursor from before every consumer had its own is
 * `SyncEntry:<database>`, until [SharedSyncEntryMigration] has split it up.
 *
 * The documents live in [BACKEND_STATE_DATABASE] rather than in a watched database: writing the
 * cursor into a database that change detection polls would make every write produce a change,
 * which would advance the cursor again.
 *
 * A missing cursor means "first run", so the poll starts from "now". That answer must only be
 * given when the cursor document is really absent: [CouchDbClient.getDatabaseDocument] reports any
 * 4xx as [NotFoundException], and treating a missing database or an auth error as a first run
 * would move the cursor forward on every poll and silently skip every change in between.
 *
 * Change detection is the only writer and polls every few seconds, so each cursor is kept in memory
 * with the revision of its last write: a poll does not read it again, and a save names that
 * revision instead of looking it up first. The memory is keyed by document id, because every
 * consumer of a database writes its own document with its own revision. A failed save forgets the
 * cursor, so the next poll reads it afresh. That is also how a cursor edited by hand in CouchDB is
 * picked up: on the next save of that cursor, which fails on the stale revision, or on a restart.
 */
class CouchDbSyncRepository(
    private val couchDbClient: CouchDbClient
) : SyncRepository {
    companion object {
        const val DOCUMENT_PREFIX = "SyncEntry"
    }

    enum class CouchDbSyncRepositoryError : AamErrorCode {
        STATE_DATABASE_MISSING
    }

    /** The last cursor read or written per document id, with the revision that write produced. */
    private data class KnownEntry(
        val entry: SyncEntry,
        val rev: String
    )

    private val known = ConcurrentHashMap<String, KnownEntry>()

    override fun findByDatabase(
        database: String,
        consumer: String?
    ): Optional<SyncEntry> {
        val documentId = documentId(database, consumer)
        known[documentId]?.let { return Optional.of(it.entry) }

        val document = readDocument(documentId) ?: return Optional.empty()
        val entry =
            SyncEntry(database = document.database, latestRef = document.latestRef, consumer = document.consumer)
        known[documentId] = KnownEntry(entry, document.rev)
        return Optional.of(entry)
    }

    private fun readDocument(documentId: String): SyncEntryDocument? =
        try {
            couchDbClient.getDatabaseDocument(
                database = BACKEND_STATE_DATABASE,
                documentId = documentId,
                kClass = SyncEntryDocument::class
            )
        } catch (ex: NotFoundException) {
            // throws on its own for any 4xx other than 404, e.g. an auth error
            if (!couchDbClient.databaseExists(BACKEND_STATE_DATABASE)) {
                throw ExternalSystemException(
                    message = "Database $BACKEND_STATE_DATABASE does not exist, cannot read the sync cursor",
                    cause = ex,
                    code = CouchDbSyncRepositoryError.STATE_DATABASE_MISSING
                )
            }
            null
        }

    override fun findAll(): List<SyncEntry> =
        couchDbClient.getDatabaseDocumentsByPrefix(
            database = BACKEND_STATE_DATABASE,
            prefix = DOCUMENT_PREFIX,
            kClass = SyncEntry::class
        )

    /** Creates the cursor if none was found, and otherwise writes over the revision last seen. */
    override fun save(syncEntry: SyncEntry): SyncEntry {
        val documentId = documentId(syncEntry.database, syncEntry.consumer)
        val result =
            try {
                couchDbClient.putDatabaseDocumentAtRevision(
                    database = BACKEND_STATE_DATABASE,
                    documentId = documentId,
                    body = syncEntry,
                    expectedRev = known[documentId]?.rev
                )
            } catch (ex: Exception) {
                known.remove(documentId)
                throw ex
            }
        known[documentId] = KnownEntry(syncEntry, result.rev)
        return syncEntry
    }

    override fun delete(syncEntry: SyncEntry) {
        val documentId = documentId(syncEntry.database, syncEntry.consumer)
        known.remove(documentId)
        couchDbClient.deleteDatabaseDocument(database = BACKEND_STATE_DATABASE, documentId = documentId)
    }

    private fun documentId(
        database: String,
        consumer: String?
    ) = if (consumer == null) "$DOCUMENT_PREFIX:$database" else "$DOCUMENT_PREFIX:$database:$consumer"
}
