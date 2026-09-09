package com.aamdigital.aambackendservice.common.changes

import java.util.Optional

/**
 * Latest processed CouchDB `_changes` sequence for one database.
 *
 * A missing entry is not an error: [CouchDbChangesProcessor] then starts from the database's
 * current `update_seq`, deliberately skipping the historic backlog.
 */
data class SyncEntry(
    val database: String,
    val latestRef: String
)

/**
 * Stores the latest sync sequence per database for change detection.
 */
interface SyncRepository {
    fun findByDatabase(database: String): Optional<SyncEntry>

    fun findAll(): List<SyncEntry>

    fun save(syncEntry: SyncEntry): SyncEntry
}
