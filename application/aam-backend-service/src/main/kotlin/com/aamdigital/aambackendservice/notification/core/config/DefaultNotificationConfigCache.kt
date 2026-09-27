package com.aamdigital.aambackendservice.notification.core.config

import com.aamdigital.aambackendservice.common.condition.DocumentCondition
import com.aamdigital.aambackendservice.common.condition.DocumentConditionEngine
import com.aamdigital.aambackendservice.common.couchdb.core.CouchDbClient
import com.aamdigital.aambackendservice.common.couchdb.core.getEmptyQueryParams
import com.aamdigital.aambackendservice.common.error.NotFoundException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * CouchDB-backed in-memory implementation of [NotificationConfigCache].
 *
 * All `NotificationConfig:*` documents are loaded on first use, and one entry is updated whenever
 * a matching document change is handled. Loading lazily rather than warming up at startup means
 * the first change is never matched against a cache that has not finished loading - which used to
 * drop that change's notifications silently - and a failed load throws, so it is logged as a failed
 * change instead of looking like "no rule matched".
 */
class DefaultNotificationConfigCache(
    private val couchDbClient: CouchDbClient,
    private val objectMapper: ObjectMapper,
    private val documentConditionEngine: DocumentConditionEngine = DocumentConditionEngine()
) : NotificationConfigCache {
    companion object {
        private const val DATABASE = "app"
        private const val DOCUMENT_PREFIX = "NotificationConfig"
    }

    private val logger = LoggerFactory.getLogger(javaClass)
    private val cache = ConcurrentHashMap<String, NotificationConfigCacheEntry>()
    private val cacheLock = Any()
    private var loaded = false

    override fun findAll(): List<NotificationConfigCacheEntry> =
        synchronized(cacheLock) {
            if (!loaded) {
                refreshAll()
            }

            cache.values.toList()
        }

    /**
     * Reloads from CouchDB. The fetch happens while holding [cacheLock] by design: the only reader
     * is the single-threaded notification change-detection path, and the lock rules out two
     * concurrent loads.
     */
    override fun refreshAll() {
        synchronized(cacheLock) {
            val nextCache =
                couchDbClient
                    .getDatabaseDocumentsByPrefix(
                        database = DATABASE,
                        prefix = DOCUMENT_PREFIX,
                        kClass = ObjectNode::class
                    ).mapNotNull { doc -> parseConfigFromDoc(doc = doc) }
                    .associateBy { it.userIdentifier }

            cache.clear()
            cache.putAll(nextCache)
            loaded = true

            logger.debug("Loaded {} notification configs into memory cache", cache.size)
        }
    }

    override fun refreshConfig(
        database: String,
        notificationConfigId: String,
        deleted: Boolean
    ) {
        val userIdentifier = extractUserIdentifier(notificationConfigId) ?: return

        if (deleted) {
            synchronized(cacheLock) {
                cache.remove(userIdentifier)
            }
            logger.debug("Removed notification config from cache: {}", notificationConfigId)
            return
        }

        val notificationConfig =
            try {
                couchDbClient.getDatabaseDocument(
                    database = database,
                    documentId = notificationConfigId,
                    queryParams = getEmptyQueryParams(),
                    kClass = NotificationConfigDto::class
                )
            } catch (
                @Suppress("SwallowedException") ex: NotFoundException
            ) {
                synchronized(cacheLock) {
                    cache.remove(userIdentifier)
                }
                logger.debug(
                    "Notification config not found during refresh, removed from cache: {}",
                    notificationConfigId
                )
                return
            }

        try {
            synchronized(cacheLock) {
                cache[userIdentifier] = toCacheEntry(notificationConfig)
            }
            logger.debug("Refreshed notification config in cache: {}", notificationConfigId)
        } catch (ex: Exception) {
            synchronized(cacheLock) {
                cache.remove(userIdentifier)
            }
            logger.error(
                "Skipping invalid NotificationConfig during refresh: notificationConfigId={}, userIdentifier={}",
                notificationConfigId,
                userIdentifier,
                ex
            )
        }
    }

    private fun parseConfigFromDoc(doc: ObjectNode): NotificationConfigCacheEntry? =
        try {
            val dto = objectMapper.convertValue(doc, NotificationConfigDto::class.java)
            toCacheEntry(dto)
        } catch (ex: Exception) {
            logger.warn("Skipping invalid NotificationConfig document while loading the cache", ex)
            null
        }

    private fun toCacheEntry(notificationConfig: NotificationConfigDto): NotificationConfigCacheEntry {
        val userIdentifier =
            extractUserIdentifier(notificationConfig.id)
                ?: throw IllegalArgumentException("Invalid NotificationConfig ID format: ${notificationConfig.id}")

        return NotificationConfigCacheEntry(
            userIdentifier = userIdentifier,
            channelPush = notificationConfig.channels?.push ?: false,
            channelEmail = notificationConfig.channels?.email ?: false,
            rules = mapToNotificationRules(notificationConfig)
        )
    }

    private fun extractUserIdentifier(notificationConfigId: String): String? {
        val parts = notificationConfigId.split(":", limit = 2)
        if (parts.size != 2 || parts.first() != DOCUMENT_PREFIX || parts[1].isBlank()) {
            logger.warn("Invalid NotificationConfig ID format: {}", notificationConfigId)
            return null
        }
        return parts[1]
    }

    private fun mapToNotificationRules(notificationConfig: NotificationConfigDto): List<NotificationRuleCacheEntry> =
        notificationConfig.notificationRules.withIndex().flatMap { (ruleIndex, rule) ->
            val conditionGroups =
                documentConditionEngine.parseConditionGroups(rule.conditions)

            rule.changeType.flatMap { changeType ->
                conditionGroups.withIndex().map { (conditionGroupIndex, conditions) ->
                    val externalIdentifierInput =
                        ExternalIdentifierInput(
                            notificationConfigId = notificationConfig.id,
                            ruleIndex = ruleIndex,
                            label = rule.label,
                            entityType = rule.entityType,
                            changeType = changeType,
                            conditionGroupIndex = conditionGroupIndex,
                            conditions = conditions
                        )

                    NotificationRuleCacheEntry(
                        label = rule.label,
                        externalIdentifier = buildExternalIdentifier(externalIdentifierInput),
                        notificationType = rule.notificationType,
                        entityType = rule.entityType,
                        changeType = changeType,
                        conditions = conditions,
                        enabled = rule.enabled
                    )
                }
            }
        }

    private data class ExternalIdentifierInput(
        val notificationConfigId: String,
        val ruleIndex: Int,
        val label: String,
        val entityType: String,
        val changeType: String,
        val conditionGroupIndex: Int,
        val conditions: List<DocumentCondition>
    )

    private fun buildExternalIdentifier(input: ExternalIdentifierInput): String {
        val conditionSignature =
            input.conditions.joinToString(separator = "|") { "${it.field}:${it.operator}:${it.value}" }
        val stableIdentifier =
            listOf(
                input.notificationConfigId,
                input.ruleIndex.toString(),
                input.label,
                input.entityType,
                input.changeType,
                input.conditionGroupIndex.toString(),
                conditionSignature
            ).joinToString("#")

        return UUID.nameUUIDFromBytes(stableIdentifier.toByteArray(StandardCharsets.UTF_8)).toString()
    }
}
