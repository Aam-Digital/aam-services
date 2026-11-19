package com.aamdigital.aambackendservice.notification.core.config

import com.aamdigital.aambackendservice.common.cache.LazySnapshot
import com.aamdigital.aambackendservice.common.couchdb.core.CouchDbClient
import com.aamdigital.aambackendservice.common.couchdb.core.getEmptyQueryParams
import com.aamdigital.aambackendservice.common.error.NotFoundException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration
import kotlin.reflect.KClass

/**
 * Keeps the notification rules of one kind of `<DocumentType>:*` document in memory.
 *
 * Documents are loaded on first use (see [LazySnapshot]), and a single entry is updated whenever a
 * matching document change is handled. Loading lazily rather than warming up at startup means the
 * first change is never matched against a cache that has not finished loading - which used to drop
 * that change's notifications silently - and a failed load throws, so it is logged as a failed
 * change instead of looking like "no rule matched". After a failed reload the previous copy is
 * served and the reload retried after [RELOAD_RETRY_INTERVAL], so an outage does not stop all
 * notifications.
 *
 * @param ttl how long the loaded configs are used before they are loaded again; `null` keeps them
 *     until the service restarts (they are still refreshed on every config document change)
 */
abstract class DocumentBackedNotificationConfigCache<D : Any>(
    private val couchDbClient: CouchDbClient,
    private val objectMapper: ObjectMapper,
    private val documentType: String,
    private val documentClass: KClass<D>,
    ttl: Duration? = null,
    clock: Clock = Clock.systemUTC()
) : NotificationConfigCache {
    companion object {
        private const val DATABASE = "app"

        /** How long the previous copy is used after a failed reload before trying again. */
        val RELOAD_RETRY_INTERVAL: Duration = Duration.ofMinutes(5)
    }

    protected val logger = LoggerFactory.getLogger(javaClass)

    override val sourceEntityType: String get() = documentType

    /** Cache entries by the identifier the document is keyed on. */
    private val configs = LazySnapshot(ttl, clock, RELOAD_RETRY_INTERVAL) { loadConfigs() }

    override fun findAll(): List<NotificationConfigCacheEntry> = configs.get().values.toList()

    /** `null` when the document yields no rules, so that it stops matching. */
    protected abstract fun toCacheEntry(document: D): NotificationConfigCacheEntry?

    override fun refreshConfig(
        database: String,
        notificationConfigId: String,
        deleted: Boolean
    ) {
        val identifier = extractIdentifier(notificationConfigId) ?: return

        if (deleted) {
            remove(identifier, notificationConfigId, "it was deleted")
            return
        }

        val document =
            try {
                couchDbClient.getDatabaseDocument(
                    database = database,
                    documentId = notificationConfigId,
                    queryParams = getEmptyQueryParams(),
                    kClass = documentClass
                )
            } catch (
                @Suppress("SwallowedException") ex: NotFoundException
            ) {
                remove(identifier, notificationConfigId, "it was not found")
                return
            }

        val entry =
            try {
                toCacheEntry(document)
            } catch (ex: Exception) {
                configs.update { it - identifier }
                logger.error("Skipping invalid {} during refresh: {}", documentType, notificationConfigId, ex)
                return
            }

        if (entry == null) {
            remove(identifier, notificationConfigId, "it no longer defines any rule")
            return
        }

        configs.update { it + (identifier to entry) }
        logger.debug("Refreshed {} in cache: {}", documentType, notificationConfigId)
    }

    private fun remove(
        identifier: String,
        documentId: String,
        reason: String
    ) {
        configs.update { it - identifier }
        logger.debug("Removed {} from cache because {}: {}", documentType, reason, documentId)
    }

    private fun loadConfigs(): Map<String, NotificationConfigCacheEntry> =
        couchDbClient
            .getDatabaseDocumentsByPrefix(
                database = DATABASE,
                prefix = documentType,
                kClass = ObjectNode::class
            ).mapNotNull { doc -> parseConfigFromDoc(doc) }
            .associateBy { it.userIdentifier }
            .also { loaded -> logger.debug("Loaded {} {} documents into memory cache", loaded.size, documentType) }

    private fun parseConfigFromDoc(doc: ObjectNode): NotificationConfigCacheEntry? =
        try {
            toCacheEntry(objectMapper.convertValue(doc, documentClass.java))
        } catch (ex: Exception) {
            logger.warn("Skipping invalid {} document while loading the cache", documentType, ex)
            null
        }

    /** The part after the `<DocumentType>:` prefix, which the entries are keyed on. */
    protected fun extractIdentifier(documentId: String): String? {
        val parts = documentId.split(":", limit = 2)
        if (parts.size != 2 || parts.first() != documentType || parts[1].isBlank()) {
            logger.warn("Invalid {} ID format: {}", documentType, documentId)
            return null
        }
        return parts[1]
    }
}
