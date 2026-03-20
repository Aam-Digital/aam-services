package com.aamdigital.aambackendservice.notification.core.config

import com.aamdigital.aambackendservice.common.condition.DocumentCondition
import com.aamdigital.aambackendservice.common.condition.DocumentConditionEngine
import com.aamdigital.aambackendservice.common.couchdb.core.CouchDbClient
import com.aamdigital.aambackendservice.common.couchdb.core.getEmptyQueryParams
import com.aamdigital.aambackendservice.common.couchdb.core.getQueryParamsAllDocs
import com.aamdigital.aambackendservice.common.couchdb.dto.CouchDbSearchResponse
import com.aamdigital.aambackendservice.common.error.NotFoundException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * CouchDB-backed in-memory implementation of [NotificationConfigCache].
 *
 * It preloads all `NotificationConfig:*` documents on startup and updates one entry
 * whenever a matching document change event is consumed.
 */
class DefaultNotificationConfigCache(
    private val couchDbClient: CouchDbClient,
    private val objectMapper: ObjectMapper,
    private val documentConditionEngine: DocumentConditionEngine = DocumentConditionEngine()
) : NotificationConfigCache {
    companion object {
        private const val DATABASE = "app"
        private const val DOCUMENT_PREFIX = "NotificationConfig"
        private const val INIT_MAX_RETRIES = 5
        private const val INIT_RETRY_DELAY_MS = 5000L
    }

    private val logger = LoggerFactory.getLogger(javaClass)
    private val cache = ConcurrentHashMap<String, NotificationConfigCacheEntry>()

    @PostConstruct
    fun init() {
        var lastException: Exception? = null
        for (attempt in 1..INIT_MAX_RETRIES) {
            try {
                refreshAll()
                return
            } catch (ex: Exception) {
                lastException = ex
                logger.warn(
                    "Failed to load notification configs on startup (attempt {}/{}): {}",
                    attempt,
                    INIT_MAX_RETRIES,
                    ex.message
                )
                if (attempt < INIT_MAX_RETRIES) {
                    Thread.sleep(INIT_RETRY_DELAY_MS)
                }
            }
        }
        throw IllegalStateException(
            "Failed to initialize NotificationConfigCache after $INIT_MAX_RETRIES attempts",
            lastException
        )
    }

    override fun findAll(): List<NotificationConfigCacheEntry> = cache.values.toList()

    override fun refreshAll() {
        val response =
            couchDbClient.getDatabaseDocument(
                database = DATABASE,
                documentId = "_all_docs",
                queryParams = getQueryParamsAllDocs(DOCUMENT_PREFIX),
                kClass = CouchDbSearchResponse::class
            )

        val nextCache =
            response.rows.mapNotNull { row ->
                parseConfigFromDoc(doc = row.doc)
            }.associateBy { it.userIdentifier }

        cache.keys.retainAll(nextCache.keys)
        cache.putAll(nextCache)

        logger.debug("Loaded {} notification configs into memory cache", cache.size)
    }

    override fun refreshConfig(
        database: String,
        notificationConfigId: String,
        deleted: Boolean
    ) {
        val userIdentifier = extractUserIdentifier(notificationConfigId) ?: return

        if (deleted) {
            cache.remove(userIdentifier)
            logger.debug("Removed notification config from cache: {}", notificationConfigId)
            return
        }

        val notificationConfig =
            try {
                couchDbClient.getDatabaseDocument(
                    database = DATABASE,
                    documentId = notificationConfigId,
                    queryParams = getEmptyQueryParams(),
                    kClass = NotificationConfigDto::class
                )
            } catch (
                @Suppress("SwallowedException") ex: NotFoundException
            ) {
                cache.remove(userIdentifier)
                logger.debug("Notification config not found during refresh, removed from cache: {}", notificationConfigId)
                return
            }

        cache[userIdentifier] = toCacheEntry(notificationConfig)
        logger.debug("Refreshed notification config in cache: {}", notificationConfigId)
    }

    private fun parseConfigFromDoc(doc: ObjectNode): NotificationConfigCacheEntry? =
        try {
            val dto = objectMapper.convertValue(doc, NotificationConfigDto::class.java)
            toCacheEntry(dto)
        } catch (ex: Exception) {
            logger.warn("Skipping invalid NotificationConfig document in cache warmup", ex)
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
                    NotificationRuleCacheEntry(
                        label = rule.label,
                        externalIdentifier =
                            buildExternalIdentifier(
                                notificationConfigId = notificationConfig.id,
                                ruleIndex = ruleIndex,
                                label = rule.label,
                                entityType = rule.entityType,
                                changeType = changeType,
                                conditionGroupIndex = conditionGroupIndex,
                                conditions = conditions
                            ),
                        notificationType = rule.notificationType,
                        entityType = rule.entityType,
                        changeType = changeType,
                        conditions = conditions,
                        enabled = rule.enabled
                    )
                }
            }
        }

    private fun buildExternalIdentifier(
        notificationConfigId: String,
        ruleIndex: Int,
        label: String,
        entityType: String,
        changeType: String,
        conditionGroupIndex: Int,
        conditions: List<DocumentCondition>
    ): String {
        val conditionSignature =
            conditions.joinToString(separator = "|") { "${it.field}:${it.operator}:${it.value}" }
        val stableIdentifier =
            listOf(
                notificationConfigId,
                ruleIndex.toString(),
                label,
                entityType,
                changeType,
                conditionGroupIndex.toString(),
                conditionSignature
            ).joinToString("#")

        return UUID.nameUUIDFromBytes(stableIdentifier.toByteArray(StandardCharsets.UTF_8)).toString()
    }
}
