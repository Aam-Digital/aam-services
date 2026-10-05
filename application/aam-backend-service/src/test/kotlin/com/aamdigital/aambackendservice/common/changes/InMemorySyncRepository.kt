package com.aamdigital.aambackendservice.common.changes

import java.util.Optional

/** [SyncRepository] over a map, keyed like the CouchDB documents are: database and consumer. */
class InMemorySyncRepository : SyncRepository {
    val entries = mutableMapOf<Pair<String, String>, SyncEntry>()

    override fun findByDatabase(
        database: String,
        consumer: String
    ): Optional<SyncEntry> = Optional.ofNullable(entries[database to consumer])

    override fun findAll(): List<SyncEntry> = entries.values.toList()

    override fun save(syncEntry: SyncEntry): SyncEntry {
        entries[syncEntry.database to syncEntry.consumer] = syncEntry
        return syncEntry
    }
}
