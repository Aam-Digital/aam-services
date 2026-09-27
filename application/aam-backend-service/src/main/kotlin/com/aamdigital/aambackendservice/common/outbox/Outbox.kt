package com.aamdigital.aambackendservice.common.outbox

import com.aamdigital.aambackendservice.common.couchdb.core.CouchDbClient
import com.aamdigital.aambackendservice.common.couchdb.core.CouchDbInitializer
import com.aamdigital.aambackendservice.common.couchdb.core.DatabaseRequest
import com.aamdigital.aambackendservice.common.couchdb.core.documentExists
import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.web.client.HttpClientErrorException
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
 * Entries are deleted once delivered, so the database is usually empty. Parked entries stay until
 * they are delivered, though, and pile up for as long as their cause does (wrong SMTP credentials,
 * say), so the frequent drain reads only [fetchRetryable] entries and never the parked ones.
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
        if (couchDbClient.documentExists(database = database, documentId = id)) {
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

    /** Entries with fewer than [maxAttempts] attempts: due or backing off, but not parked. */
    fun fetchRetryable(maxAttempts: Int): List<OutboxEntry<P>> =
        find(mapOf("attempts" to mapOf("\$lt" to maxAttempts)))

    /** Entries that have used up [maxAttempts] and are parked. */
    fun fetchParked(maxAttempts: Int): List<OutboxEntry<P>> =
        find(mapOf("attempts" to mapOf("\$gte" to maxAttempts)))

    /**
     * Returns an empty list when the database does not exist: nothing has ever been owed, or the
     * database was dropped out from under us, and in both cases there is no pending work.
     */
    private fun find(selector: Map<String, Any>): List<OutboxEntry<P>> =
        try {
            couchDbClient
                .findDatabaseDocumentsByPrefix(
                    database = database,
                    prefix = OutboxEntry.ID_PREFIX,
                    selector = selector,
                    kClass = JsonNode::class
                ).map { document -> objectMapper.convertValue<OutboxEntry<P>>(document, entryType) }
        } catch (ex: HttpClientErrorException.NotFound) {
            logger.debug("No {} database yet, nothing is waiting", database, ex)
            emptyList()
        }
}
