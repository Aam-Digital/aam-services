package com.aamdigital.aambackendservice.changes.core

import com.aamdigital.aambackendservice.changes.core.event.DatabaseChangeEvent
import com.aamdigital.aambackendservice.changes.di.ChangesQueueConfiguration.Companion.DB_CHANGES_QUEUE
import com.aamdigital.aambackendservice.changes.repository.SyncEntry
import com.aamdigital.aambackendservice.changes.repository.SyncRepository
import com.aamdigital.aambackendservice.couchdb.core.CouchDbStorage
import com.aamdigital.aambackendservice.couchdb.core.getEmptyQueryParams
import org.slf4j.LoggerFactory
import reactor.core.publisher.Mono
import java.util.*

class CouchDatabaseChangeDetection(
    private val couchDbStorage: CouchDbStorage,
    private val documentChangeEventPublisher: ChangeEventPublisher,
    private val syncRepository: SyncRepository,
) : DatabaseChangeDetection {
    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        private val LATEST_REFS: MutableMap<String, String> = Collections.synchronizedMap(hashMapOf())
        private const val CHANGES_LIMIT: Int = 100
    }

    /**
     * Will reach out to CouchDb and convert _changes to  Domain.DocumentChangeEvent's
     */
    override fun checkForChanges(): Mono<Unit> {
        logger.trace("[CouchDatabaseChangeDetection] start couchdb change detection...")
        return couchDbStorage.allDatabases().flatMap { databases ->
            val requests = databases.filter { !it.startsWith("_") }.map { database ->
                fetchChangesForDatabase(database)
            }
            Mono.zip(requests) {
                it.map { }
            }
        }.map {
            logger.trace("[CouchDatabaseChangeDetection] ...completed couchdb change detection.")
        }
    }

    private fun fetchChangesForDatabase(database: String): Mono<Unit> {
        logger.trace("[CouchDatabaseChangeDetection] check changes for database \"{}\"...", database)

        return syncRepository.findByDatabase(database).defaultIfEmpty(SyncEntry(database = database, latestRef = ""))
            .flatMap {
                LATEST_REFS[database] = it.latestRef

                val queryParams = getEmptyQueryParams()

                if (LATEST_REFS.containsKey(database) && LATEST_REFS.getValue(database).isNotEmpty()) {
                    queryParams.set("last-event-id", LATEST_REFS.getValue(database))
                }

                queryParams.set("limit", CHANGES_LIMIT.toString())
                queryParams.set("include_docs", "true")

                couchDbStorage.changes(
                    database = database, queryParams = queryParams
                )
            }
            .map { changes ->
                changes.results.forEachIndexed { index, couchDbChangeResult ->
                    logger.trace("$database $index: {}", couchDbChangeResult.toString())

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
            }
            .flatMap {
                syncRepository.findByDatabase(database).defaultIfEmpty(SyncEntry(database = database, latestRef = ""))
            }
            .flatMap {
                it.latestRef = LATEST_REFS[database].orEmpty()
                syncRepository.save(it)
            }
            .map {
                logger.trace("[CouchDatabaseChangeDetection] ...completed changes check for database \"{}\".", database)
            }
    }
}
