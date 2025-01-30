package com.aamdigital.aambackendservice.changes.core

import com.aamdigital.aambackendservice.changes.di.ChangesQueueConfiguration.Companion.DB_CHANGES_QUEUE
import com.aamdigital.aambackendservice.changes.domain.DatabaseChangeEvent
import com.aamdigital.aambackendservice.changes.repository.SyncEntry
import com.aamdigital.aambackendservice.changes.repository.SyncRepository
import com.aamdigital.aambackendservice.couchdb.core.CouchDbClient
import com.aamdigital.aambackendservice.couchdb.core.getEmptyQueryParams
import com.aamdigital.aambackendservice.error.AamErrorCode
import com.aamdigital.aambackendservice.error.InvalidArgumentException
import com.fasterxml.jackson.databind.node.ObjectNode
import java.util.*
import kotlin.jvm.optionals.getOrDefault

class CouchDbDatabaseChangeDetection(
    private val couchDbClient: CouchDbClient,
    private val documentChangeEventPublisher: ChangeEventPublisher,
    private val syncRepository: SyncRepository,
) : DatabaseChangeDetection {
    companion object {
        private val LATEST_REFS: MutableMap<String, String> = Collections.synchronizedMap(hashMapOf())
        private const val CHANGES_LIMIT: Int = 100
    }

    enum class CouchDbChangeDetectionError : AamErrorCode {
        COULD_NOT_FETCH_LATEST_REF
    }

    /**
     * Will reach out to CouchDb and convert _changes to  Domain.DocumentChangeEvent's
     */
    override fun checkForChanges() {
        couchDbClient
            .allDatabases()
            .filter { !it.startsWith("_") }.map { database ->
                fetchChangesForDatabase(database)
            }
    }

    private fun getLatestRef(database: String): String {
        return try {
            couchDbClient.getDatabaseDocument(
                database = database,
                documentId = "",
                kClass = ObjectNode::class
            ).get("update_seq").textValue()
        } catch (ex: Exception) {
            throw InvalidArgumentException(
                message = "Could not fetch latest update_seq",
                cause = ex,
                code = CouchDbChangeDetectionError.COULD_NOT_FETCH_LATEST_REF
            )
        }
    }

    private fun fetchChangesForDatabase(database: String) {
        var syncEntry =
            syncRepository.findByDatabase(database)
                .getOrDefault(SyncEntry(database = database, latestRef = getLatestRef(database)))

        LATEST_REFS[database] = syncEntry.latestRef

        val queryParams = getEmptyQueryParams()

        if (LATEST_REFS.containsKey(database) && LATEST_REFS.getValue(database).isNotEmpty()) {
            queryParams.set("last-event-id", LATEST_REFS.getValue(database))
        }

        queryParams.set("limit", CHANGES_LIMIT.toString())
        queryParams.set("include_docs", "true")

        val changes = couchDbClient.changes(
            database = database, queryParams = queryParams
        )

        changes.results.forEachIndexed { _, couchDbChangeResult ->
            val rev = couchDbChangeResult.doc?.get("_rev")?.textValue()

            if (!couchDbChangeResult.id.startsWith("_design")) {
                documentChangeEventPublisher.publish(
                    channel = DB_CHANGES_QUEUE,
                    DatabaseChangeEvent(
                        documentId = couchDbChangeResult.id,
                        database = database,
                        rev = rev,
                        deleted = couchDbChangeResult.deleted == true
                    )
                )
            }

            LATEST_REFS[database] = couchDbChangeResult.seq
        }

        syncEntry =
            syncRepository.findByDatabase(database)
                .getOrDefault(SyncEntry(database = database, latestRef = getLatestRef(database)))

        syncEntry.latestRef = LATEST_REFS[database].orEmpty()
        syncRepository.save(syncEntry)
    }
}
