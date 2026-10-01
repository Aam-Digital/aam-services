package com.aamdigital.aambackendservice.common.changes

import java.util.Optional

/**
 * Latest CouchDB `_changes` sequence one change consumer has processed in one database.
 *
 * Every [DocumentChangeHandler] has its own cursor, so each module moves through the change feed at
 * its own pace and a slow or stuck module holds back only itself.
 *
 * A missing entry is not an error: [CouchDbChangesProcessor] then starts from the database's
 * current `update_seq`, deliberately skipping the historic backlog.
 */
data class SyncEntry(
    val database: String,
    val latestRef: String,
    /**
     * The [DocumentChangeHandler.consumerName] this cursor belongs to. Null only for the one cursor
     * all consumers shared before each had its own, which [SharedSyncEntryMigration] splits up.
     */
    val consumer: String? = null
)

/**
 * Stores the latest sync sequence per database and change consumer.
 */
interface SyncRepository {
    /** @param consumer null for the cursor that all consumers shared before each had its own */
    fun findByDatabase(
        database: String,
        consumer: String?
    ): Optional<SyncEntry>

    fun findAll(): List<SyncEntry>

    fun save(syncEntry: SyncEntry): SyncEntry

    fun delete(syncEntry: SyncEntry)
}
