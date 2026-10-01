package com.aamdigital.aambackendservice.notification.core.config

import com.aamdigital.aambackendservice.common.cache.LazySnapshot
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

/**
 * CouchDB-backed in-memory implementation of [NotificationConfigCache].
 *
 * All `NotificationConfig:*` documents are loaded on first use (see [LazySnapshot]), and one entry
 * is updated whenever a matching document change is handled. Loading lazily rather than warming up
 * at startup means the first change is never matched against a cache that has not finished loading
 * - which used to drop that change's notifications silently - and a failed load throws, so it is
 * logged as a failed change instead of looking like "no rule matched".
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

    /** Cache entries by user identifier. */
    private val configs = LazySnapshot { loadConfigs() }

    override fun findAll(): List<NotificationConfigCacheEntry> = configs.get().values.toList()

    override fun refreshConfig(
        database: String,
        notificationConfigId: String,
        deleted: Boolean
    ) {
        val userIdentifier = extractUserIdentifier(notificationConfigId) ?: return

        if (deleted) {
            configs.update { it - userIdentifier }
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
                configs.update { it - userIdentifier }
                logger.debug(
                    "Notification config not found during refresh, removed from cache: {}",
                    notificationConfigId
                )
                return
            }

        val entry =
            try {
                toCacheEntry(notificationConfig)
            } catch (ex: Exception) {
                configs.update { it - userIdentifier }
                logger.error(
                    "Skipping invalid NotificationConfig during refresh: notificationConfigId={}, userIdentifier={}",
                    notificationConfigId,
                    userIdentifier,
                    ex
                )
                return
            }

        configs.update { it + (userIdentifier to entry) }
        logger.debug("Refreshed notification config in cache: {}", notificationConfigId)
    }

    private fun loadConfigs(): Map<String, NotificationConfigCacheEntry> =
        couchDbClient
            .getDatabaseDocumentsByPrefix(
                database = DATABASE,
                prefix = DOCUMENT_PREFIX,
                kClass = ObjectNode::class
            ).mapNotNull { doc -> parseConfigFromDoc(doc = doc) }
            .associateBy { it.userIdentifier }
            .also { loaded -> logger.debug("Loaded {} notification configs into memory cache", loaded.size) }

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
