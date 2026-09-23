package com.aamdigital.aambackendservice.common.storage.migration

import com.aamdigital.aambackendservice.common.couchdb.core.BACKEND_STATE_DATABASE
import com.aamdigital.aambackendservice.common.couchdb.core.CouchDbClient
import com.aamdigital.aambackendservice.common.error.NotFoundException
import com.fasterxml.jackson.annotation.JsonFormat
import java.time.Instant

/** Records which one-shot migration steps have already completed on this instance. */
interface MigrationStateStore {
    fun isCompleted(step: String): Boolean

    fun markCompleted(step: String)
}

/** Marker document for one completed migration step. */
data class MigrationStepState(
    val step: String,
    /**
     * The shared ObjectMapper leaves WRITE_DATES_AS_TIMESTAMPS enabled, so without this an Instant
     * is stored as a numeric epoch value instead of a readable ISO-8601 string.
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    val completedAt: Instant
)

/**
 * [MigrationStateStore] backed by one `Migration:<step>` document per step in
 * [BACKEND_STATE_DATABASE], next to the state the migration writes.
 */
class CouchDbMigrationStateStore(
    private val couchDbClient: CouchDbClient
) : MigrationStateStore {
    companion object {
        const val DOCUMENT_PREFIX = "Migration"
    }

    override fun isCompleted(step: String): Boolean =
        try {
            couchDbClient.getDatabaseDocument(
                database = BACKEND_STATE_DATABASE,
                documentId = documentId(step),
                kClass = MigrationStepState::class
            )
            true
        } catch (_: NotFoundException) {
            false
        }

    override fun markCompleted(step: String) {
        couchDbClient.putDatabaseDocument(
            database = BACKEND_STATE_DATABASE,
            documentId = documentId(step),
            body = MigrationStepState(step = step, completedAt = Instant.now())
        )
    }

    private fun documentId(step: String) = "$DOCUMENT_PREFIX:$step"
}
