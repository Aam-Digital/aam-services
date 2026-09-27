package com.aamdigital.aambackendservice.common.outbox

import com.aamdigital.aambackendservice.common.couchdb.core.CouchDbClient
import com.aamdigital.aambackendservice.common.couchdb.core.CouchDbInitializer
import com.aamdigital.aambackendservice.common.couchdb.core.DatabaseRequest
import com.aamdigital.aambackendservice.common.error.NotFoundException
import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import java.time.Clock
import kotlin.reflect.KClass

/**
 * Durable record of work that must not be lost, held as [OutboxEntry] documents in its own CouchDB
 * [database] until an [OutboxDrainer] has delivered them.
 *
 * A module calls [enqueue] (or [deliverNowOrEnqueue]) and is done: the entry survives a restart,
 * and the drainer owns delivery and retries from there. See `README.md` in this package for how to
 * add an outbox.
 *
 * Entries are deleted once delivered, so in steady state the database is empty and listing it is
 * cheap. That is why [fetchAll] reads the whole key range rather than needing a Mango index.
 *
 * @param database name of the dedicated CouchDB database, also used to name this outbox in logs
 * @param payloadType what [OutboxEntry.payload] is read back as
 */
class Outbox<P : Any>(
    val database: String,
    private val payloadType: KClass<P>,
    private val couchDbClient: CouchDbClient,
    private val couchDbInitializer: CouchDbInitializer,
    private val objectMapper: ObjectMapper,
    private val clock: Clock = Clock.systemUTC()
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    // the payload type is erased at runtime, so entries are read as JSON and converted with the full type
    private val entryType: JavaType =
        objectMapper.typeFactory.constructParametricType(OutboxEntry::class.java, payloadType.java)

    /**
     * Records [payload] for delivery, unless an entry for [key] is already waiting.
     *
     * Derive [key] from whatever caused the work, so replaying the cause re-derives the same key and
     * re-enqueueing is a no-op rather than a duplicate. The existence check matters because
     * [CouchDbClient.putDatabaseDocument] is an unconditional upsert: writing blindly would reset
     * [OutboxEntry.attempts] every time the cause is replayed, and the entry would be retried forever.
     *
     * @return true when the entry was newly stored, false when it was already waiting.
     */
    fun enqueue(
        key: String,
        payload: P
    ): Boolean {
        // create on demand rather than only at startup: the database may not exist yet the first
        // time something is owed
        couchDbInitializer.createDatabase(DatabaseRequest(database))

        val id = OutboxEntry.idFor(key)
        val existing = couchDbClient.headDatabaseDocument(database = database, documentId = id)
        if (!existing.eTag.isNullOrBlank()) {
            logger.debug("{} entry {} is already waiting, not re-enqueueing", database, id)
            return false
        }

        val now = clock.instant()
        store(OutboxEntry(id = id, payload = payload, nextAttemptAt = now, createdAt = now))
        return true
    }

    /**
     * Delivers [payload] through [handler] right away, on the caller's thread, and only enqueues it
     * when that fails - for work that is quick enough to try inline but still must not be lost.
     *
     * The failed attempt is not counted: the entry starts with the full retry budget.
     */
    fun deliverNowOrEnqueue(
        key: String,
        payload: P,
        handler: OutboxHandler<P>
    ) {
        val result =
            try {
                handler.deliver(payload)
            } catch (ex: Exception) {
                OutboxDeliveryResult.Rejected(ex.localizedMessage, ex)
            }

        when (result) {
            is OutboxDeliveryResult.Delivered -> return
            is OutboxDeliveryResult.RetryLater -> warnDeferred(key, result.reason, result.cause)
            is OutboxDeliveryResult.Rejected -> warnDeferred(key, result.reason, result.cause)
        }

        enqueue(key, payload)
    }

    private fun warnDeferred(
        key: String,
        reason: String?,
        cause: Throwable?
    ) {
        logger.warn("Could not deliver {} right away, moving it to {}: {}", key, database, reason, cause)
    }

    /** Writes the entry, replacing any existing revision. Used to record a delivery attempt. */
    fun store(entry: OutboxEntry<P>) {
        couchDbClient.putDatabaseDocument(database = database, documentId = entry.id, body = entry)
    }

    fun delete(entryId: String) {
        couchDbClient.deleteDatabaseDocument(database = database, documentId = entryId)
    }

    /**
     * All entries currently in the outbox, whether due, backing off or parked.
     *
     * Returns an empty list when the database does not exist: nothing has ever been owed, or the
     * database was dropped out from under us, and in both cases there is no pending work.
     */
    fun fetchAll(): List<OutboxEntry<P>> =
        try {
            couchDbClient
                .getDatabaseDocumentsByPrefix(
                    database = database,
                    prefix = OutboxEntry.ID_PREFIX,
                    kClass = JsonNode::class
                ).map { document -> objectMapper.convertValue<OutboxEntry<P>>(document, entryType) }
        } catch (ex: NotFoundException) {
            logger.debug("No {} database yet, nothing is waiting", database, ex)
            emptyList()
        }
}
