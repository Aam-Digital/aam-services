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
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.reflect.KClass

/**
 * Durable record of work that must not be lost, held as [OutboxEntry] documents in its own CouchDB
 * [database], together with the delivery and retry policy for them.
 *
 * A module calls [enqueue] (or [deliverNowOrEnqueue]) and is done; a scheduled job calls [drain].
 * The entry survives a restart, and this owns delivery and retries from there. All a module
 * supplies is an [OutboxHandler]: deliver one payload, and say whether a failure is worth retrying.
 * See `README.md` in this package for how to add an outbox.
 *
 * Storage and retry live in one class because they are not independently useful: an outbox nothing
 * drains never delivers, and a drainer needs an outbox to read. Keeping them together is also what
 * makes a module's wiring one bean plus its handler.
 *
 * The retry policy:
 *
 * - [OutboxDeliveryResult.Delivered] deletes the entry, before anything else can observe it, so a
 *   handler that is not idempotent (sending an email, say) is not called again for it;
 * - [OutboxDeliveryResult.RetryLater] increments [OutboxEntry.attempts] and pushes
 *   [OutboxEntry.nextAttemptAt] out as [OutboxRetryPolicy.delayAfter] says;
 * - once [OutboxRetryPolicy.maxAttempts] is reached the entry is *parked*: it keeps its `lastError`
 *   and stops being retried, while staying queryable
 *   (`GET <database>/_all_docs?include_docs=true`);
 * - [OutboxDeliveryResult.Rejected], or an exception from the handler, parks the entry immediately;
 * - parked entries are un-parked once per process, which makes the operator's recovery path: fix
 *   the cause, restart the service, and held entries are retried.
 *
 * Entries are deleted once delivered, so the database is usually empty. Parked entries stay until
 * they are delivered, though, and pile up for as long as their cause does (wrong SMTP credentials,
 * say), so a regular drain reads only retryable entries and never the parked ones.
 *
 * @param database name of the dedicated CouchDB database, also used to name this outbox in logs
 * @param payloadType what [OutboxEntry.payload] is read back as
 */
class Outbox<P : Any>(
    val database: String,
    private val payloadType: KClass<P>,
    private val handler: OutboxHandler<P>,
    private val retryPolicy: OutboxRetryPolicy,
    private val couchDbClient: CouchDbClient,
    private val couchDbInitializer: CouchDbInitializer,
    private val objectMapper: ObjectMapper,
    private val clock: Clock = Clock.systemUTC()
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val parkedEntriesReset = AtomicBoolean(false)
    private val maxAttempts = retryPolicy.maxAttempts

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
     * Delivers [payload] through the handler right away, on the caller's thread, and only enqueues
     * it when that fails - for work that is quick enough to try inline but still must not be lost.
     *
     * The failed attempt is not counted: the entry starts with the full retry budget.
     */
    fun deliverNowOrEnqueue(
        key: String,
        payload: P
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

    /**
     * Delivers every entry whose next attempt is due.
     *
     * Meant to be called from a `@Scheduled` job wrapped in `ScheduledJobBackoff`.
     */
    fun drain() {
        resetParkedEntries()

        val now = clock.instant()
        fetchRetryable()
            .filter { entry -> !entry.nextAttemptAt.isAfter(now) }
            .forEach { entry -> deliver(entry) }
    }

    private fun warnDeferred(
        key: String,
        reason: String?,
        cause: Throwable?
    ) {
        logger.warn("Could not deliver {} right away, moving it to {}: {}", key, database, reason, cause)
    }

    /**
     * Un-parks entries that had run out of attempts, once per process.
     *
     * Doing it once per process rather than once per tick is what stops a permanently failing entry
     * from becoming a hot retry loop. It only counts as done once the parked entries could be read,
     * so a CouchDB outage at startup does not skip it.
     */
    private fun resetParkedEntries() {
        if (parkedEntriesReset.get()) {
            return
        }

        val parked = fetchParked()
        parkedEntriesReset.set(true)
        if (parked.isEmpty()) {
            return
        }

        logger.info(
            "Retrying {} {} entries that had exhausted their delivery attempts before this restart",
            parked.size,
            database
        )

        parked.forEach { entry ->
            try {
                store(entry.copy(attempts = 0, nextAttemptAt = clock.instant()))
            } catch (ex: Exception) {
                logger.warn("Could not un-park {} entry {}", database, entry.id, ex)
            }
        }
    }

    private fun deliver(entry: OutboxEntry<P>) {
        try {
            when (val result = handler.deliver(entry.payload)) {
                is OutboxDeliveryResult.Delivered -> {
                    delete(entry.id)
                    logger.debug("Delivered {} entry {}", database, entry.id)
                }

                is OutboxDeliveryResult.RetryLater -> {
                    scheduleRetry(entry, result.reason, result.cause)
                }

                is OutboxDeliveryResult.Rejected -> {
                    park(entry, result.reason, result.cause)
                }
            }
        } catch (ex: Exception) {
            park(entry, ex.localizedMessage, ex)
        }
    }

    private fun scheduleRetry(
        entry: OutboxEntry<P>,
        reason: String?,
        cause: Throwable?
    ) {
        val attempts = entry.attempts + 1

        if (attempts >= maxAttempts) {
            park(entry, reason, cause)
            return
        }

        val delay = retryPolicy.delayAfter(attempts)
        logger.warn(
            "Transient failure delivering {} entry {} (attempt {} of {}), retrying in {}s: {}",
            database,
            entry.id,
            attempts,
            maxAttempts,
            delay.toSeconds(),
            reason
        )

        recordAttempt(
            entry.copy(
                attempts = attempts,
                nextAttemptAt = clock.instant().plus(delay),
                lastError = reason
            )
        )
    }

    /**
     * Stops retrying the entry but keeps it, so the failure is visible and a restart can pick it up
     * again.
     */
    private fun park(
        entry: OutboxEntry<P>,
        reason: String?,
        cause: Throwable?
    ) {
        logger.error(
            "Giving up delivering {} entry {} until the service restarts: {}",
            database,
            entry.id,
            reason,
            cause
        )

        recordAttempt(
            entry.copy(
                attempts = maxAttempts,
                nextAttemptAt = clock.instant(),
                lastError = reason
            )
        )
    }

    private fun recordAttempt(entry: OutboxEntry<P>) {
        try {
            store(entry)
        } catch (ex: Exception) {
            // The entry keeps its previous state, so it is retried on a later tick rather than lost.
            logger.warn(
                "Could not record delivery attempt for {} entry {}",
                database,
                entry.id,
                ex
            )
        }
    }

    /** Writes the entry, replacing any existing revision. Used to record a delivery attempt. */
    internal fun store(entry: OutboxEntry<P>) {
        couchDbClient.putDatabaseDocument(database = database, documentId = entry.id, body = entry)
    }

    internal fun delete(entryId: String) {
        couchDbClient.deleteDatabaseDocument(database = database, documentId = entryId)
    }

    /** Entries with fewer than [maxAttempts] attempts: due or backing off, but not parked. */
    internal fun fetchRetryable(): List<OutboxEntry<P>> = find(mapOf("attempts" to mapOf("\$lt" to maxAttempts)))

    /** Entries that have used up [maxAttempts] and are parked. */
    internal fun fetchParked(): List<OutboxEntry<P>> = find(mapOf("attempts" to mapOf("\$gte" to maxAttempts)))

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
