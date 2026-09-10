package com.aamdigital.aambackendservice.notification.core.outbox

import com.aamdigital.aambackendservice.common.couchdb.core.CouchDbClient
import com.aamdigital.aambackendservice.common.couchdb.core.CouchDbInitializer
import com.aamdigital.aambackendservice.common.couchdb.core.DatabaseRequest
import com.aamdigital.aambackendservice.common.couchdb.core.getQueryParamsAllDocs
import com.aamdigital.aambackendservice.common.error.AamErrorCode
import com.aamdigital.aambackendservice.common.error.InternalServerException
import com.aamdigital.aambackendservice.common.error.NotFoundException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.slf4j.LoggerFactory

/**
 * CouchDB access for [NotificationOutboxEntry], in its own `notification-outbox` database.
 *
 * A dedicated database keeps the pending-delivery list globally queryable, which is the whole point:
 * the per-user `notifications_<user>` databases that hold delivered in-app notifications cannot be
 * swept for outstanding work, because CouchDB has no cross-database query.
 *
 * Entries are deleted once delivered, so in steady state the database is empty and listing it is
 * cheap. That is why [fetchPending] reads the whole key range rather than needing a Mango index.
 */
class NotificationOutboxRepository(
    private val couchDbClient: CouchDbClient,
    private val couchDbInitializer: CouchDbInitializer,
    private val objectMapper: ObjectMapper
) {
    enum class NotificationOutboxRepositoryErrorCode : AamErrorCode {
        INVALID_RESPONSE
    }

    companion object {
        const val OUTBOX_DATABASE = "notification-outbox"
    }

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Stores the entry unless one with the same id already exists.
     *
     * The existence check is what makes replay safe: [CouchDbClient.putDatabaseDocument] is an
     * unconditional upsert, so writing blindly would reset [NotificationOutboxEntry.attempts] every
     * time a document change is reprocessed and the entry would be retried forever.
     *
     * @return true when the entry was newly stored, false when it was already pending.
     */
    fun storeIfAbsent(entry: NotificationOutboxEntry): Boolean {
        // create on demand rather than only at startup, matching AppCreateNotificationHandler:
        // the database may not exist yet the first time a notification is owed
        couchDbInitializer.createDatabase(DatabaseRequest(OUTBOX_DATABASE))

        val existing =
            couchDbClient.headDatabaseDocument(
                database = OUTBOX_DATABASE,
                documentId = entry.id
            )

        if (!existing.eTag.isNullOrBlank()) {
            logger.debug("Notification {} is already pending delivery, not re-enqueueing", entry.id)
            return false
        }

        store(entry)
        return true
    }

    /** Writes the entry, replacing any existing revision. Used to record a delivery attempt. */
    fun store(entry: NotificationOutboxEntry) {
        couchDbClient.putDatabaseDocument(
            database = OUTBOX_DATABASE,
            documentId = entry.id,
            body = entry
        )
    }

    fun delete(entryId: String) {
        couchDbClient.deleteDatabaseDocument(
            database = OUTBOX_DATABASE,
            documentId = entryId
        )
    }

    /**
     * All entries currently in the outbox, delivered or not.
     *
     * Returns an empty list when the database does not exist: nothing has ever been owed, or the
     * database was dropped out from under us, and in both cases there is no pending work.
     */
    fun fetchPending(): List<NotificationOutboxEntry> {
        val objectNode =
            try {
                couchDbClient.getDatabaseDocument(
                    database = OUTBOX_DATABASE,
                    documentId = "_all_docs",
                    queryParams = getQueryParamsAllDocs(NotificationOutboxEntry.ID_PREFIX),
                    kClass = ObjectNode::class
                )
            } catch (ex: NotFoundException) {
                logger.debug("No {} database yet, nothing is pending delivery", OUTBOX_DATABASE, ex)
                return emptyList()
            }

        val data = objectMapper.convertValue(objectNode, Map::class.java)
        return (data["rows"] as Iterable<*>)
            .map { entry ->
                if (entry is LinkedHashMap<*, *>) {
                    objectMapper.convertValue(entry["doc"], NotificationOutboxEntry::class.java)
                } else {
                    throw InternalServerException(
                        message = "Invalid response",
                        code = NotificationOutboxRepositoryErrorCode.INVALID_RESPONSE
                    )
                }
            }
    }
}
