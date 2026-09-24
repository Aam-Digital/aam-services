package com.aamdigital.aambackendservice.common.changes

import com.aamdigital.aambackendservice.common.couchdb.core.BACKEND_STATE_DATABASE
import com.aamdigital.aambackendservice.common.couchdb.core.CouchDbClient
import com.aamdigital.aambackendservice.common.couchdb.core.fetchAllDocumentsByPrefix
import com.aamdigital.aambackendservice.common.error.AamErrorCode
import com.aamdigital.aambackendservice.common.error.ExternalSystemException
import com.aamdigital.aambackendservice.common.error.NotFoundException
import com.fasterxml.jackson.databind.ObjectMapper
import java.util.Optional

/**
 * [SyncRepository] backed by one CouchDB document per watched database.
 *
 * The documents live in [BACKEND_STATE_DATABASE] rather than in a watched database: writing the
 * cursor into a database that change detection polls would make every write produce a change,
 * which would advance the cursor again.
 *
 * A missing cursor means "first run", so the poll starts from "now". That answer must only be
 * given when the cursor document is really absent: [CouchDbClient.getDatabaseDocument] reports any
 * 4xx as [NotFoundException], and treating a missing database or an auth error as a first run
 * would move the cursor forward on every poll and silently skip every change in between.
 */
class CouchDbSyncRepository(
    private val couchDbClient: CouchDbClient,
    private val objectMapper: ObjectMapper
) : SyncRepository {
    companion object {
        const val DOCUMENT_PREFIX = "SyncEntry"
    }

    enum class CouchDbSyncRepositoryError : AamErrorCode {
        STATE_DATABASE_MISSING
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
        } catch (ex: NotFoundException) {
            // throws on its own for any 4xx other than 404, e.g. an auth error
            if (!couchDbClient.databaseExists(BACKEND_STATE_DATABASE)) {
                throw ExternalSystemException(
                    message = "Database $BACKEND_STATE_DATABASE does not exist, cannot read the sync cursor",
                    cause = ex,
                    code = CouchDbSyncRepositoryError.STATE_DATABASE_MISSING
                )
            }
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
