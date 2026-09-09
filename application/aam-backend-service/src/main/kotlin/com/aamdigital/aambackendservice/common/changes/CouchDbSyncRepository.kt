package com.aamdigital.aambackendservice.common.changes

import com.aamdigital.aambackendservice.common.couchdb.core.BACKEND_STATE_DATABASE
import com.aamdigital.aambackendservice.common.couchdb.core.CouchDbClient
import com.aamdigital.aambackendservice.common.couchdb.core.fetchAllDocumentsByPrefix
import com.aamdigital.aambackendservice.common.error.NotFoundException
import com.fasterxml.jackson.databind.ObjectMapper
import java.util.Optional

/**
 * [SyncRepository] backed by one CouchDB document per watched database.
 *
 * The documents live in [BACKEND_STATE_DATABASE] rather than in a watched database: writing the
 * cursor into a database that change detection polls would make every write produce a change,
 * which would advance the cursor again.
 */
class CouchDbSyncRepository(
    private val couchDbClient: CouchDbClient,
    private val objectMapper: ObjectMapper
) : SyncRepository {
    companion object {
        const val DOCUMENT_PREFIX = "SyncEntry"
    }

    override fun findByDatabase(database: String): Optional<SyncEntry> =
        try {
            Optional.of(
                couchDbClient.getDatabaseDocument(
                    database = BACKEND_STATE_DATABASE,
                    documentId = documentId(database),
                    kClass = SyncEntry::class
                )
            )
        } catch (_: NotFoundException) {
            Optional.empty()
        }

    override fun findAll(): List<SyncEntry> =
        fetchAllDocumentsByPrefix(
            couchDbClient = couchDbClient,
            objectMapper = objectMapper,
            database = BACKEND_STATE_DATABASE,
            prefix = DOCUMENT_PREFIX,
            kClass = SyncEntry::class
        )

    override fun save(syncEntry: SyncEntry): SyncEntry {
        couchDbClient.putDatabaseDocument(
            database = BACKEND_STATE_DATABASE,
            documentId = documentId(syncEntry.database),
            body = syncEntry
        )
        return syncEntry
    }

    private fun documentId(database: String) = "$DOCUMENT_PREFIX:$database"
}
