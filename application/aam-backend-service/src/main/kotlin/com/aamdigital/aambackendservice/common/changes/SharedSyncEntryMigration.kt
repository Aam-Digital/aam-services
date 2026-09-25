package com.aamdigital.aambackendservice.common.changes

import org.slf4j.LoggerFactory

/**
 * Splits the change-detection cursor that all consumers used to share into one cursor per consumer.
 *
 * Before every [DocumentChangeHandler] had its own cursor, a single `SyncEntry:<database>`
 * recorded how far change detection had got. After the upgrade each consumer has to continue from
 * that position: starting from "now" would skip whatever changed while the service was down for
 * the upgrade. So every registered consumer that has no cursor of its own yet gets a copy of the
 * shared one, and the shared one is then deleted.
 *
 * Deleting it is what keeps a module that is only enabled later from inheriting an arbitrarily old
 * position and replaying the whole backlog since - for notifications, mailing users about months
 * of old changes. With the shared cursor gone, such a module finds nothing and starts from "now",
 * as a newly enabled module always has.
 *
 * Runs at the start of every poll until it has succeeded once, and every poll waits for it: a
 * consumer must never start from "now" just because the shared cursor could not be read yet. A
 * failure fails the poll, which is then retried with the poll's backoff. The shared cursor the
 * PostgreSQL migration copies is already in place by then, because that migration runs before the
 * scheduled polls start.
 *
 * TODO(#209): remove once every instance has run a release containing this, together with the
 *  PostgreSQL migration, which is the only other writer of the shared cursor.
 */
class SharedSyncEntryMigration(
    private val syncRepository: SyncRepository,
    private val databases: List<String>,
    private val consumerNames: List<String>
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Volatile
    private var completed = false

    fun migrateIfPending() {
        if (completed) return

        synchronized(this) {
            if (completed) return
            databases.forEach(::splitSharedCursor)
            completed = true
        }
    }

    private fun splitSharedCursor(database: String) {
        val shared = syncRepository.findByDatabase(database, consumer = null).orElse(null) ?: return

        consumerNames
            .filter { consumer -> syncRepository.findByDatabase(database, consumer).isEmpty }
            .forEach { consumer ->
                syncRepository.save(shared.copy(consumer = consumer))
                logger.info(
                    "Change consumer {} continues database {} from the cursor it used to share with the others",
                    consumer,
                    database
                )
            }

        syncRepository.delete(shared)
    }
}
