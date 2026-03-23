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
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * CouchDB-backed in-memory implementation of [NotificationConfigCache].
 *
 * It preloads all `NotificationConfig:*` documents on startup and updates one entry
 * whenever a matching document change event is consumed.
 */
class DefaultNotificationConfigCache(
    private val couchDbClient: CouchDbClient,
    private val objectMapper: ObjectMapper,
    private val documentConditionEngine: DocumentConditionEngine = DocumentConditionEngine(),
    private val scheduleRetry: (Long, () -> Unit) -> Unit = { delayMs, task ->
        CompletableFuture.delayedExecutor(delayMs, TimeUnit.MILLISECONDS).execute(task)
    }
) : NotificationConfigCache {
    companion object {
        private const val DATABASE = "app"
        private const val DOCUMENT_PREFIX = "NotificationConfig"
        private const val INIT_MAX_RETRIES = 5
        private const val INIT_INITIAL_RETRY_DELAY_MS = 5000L
        private const val INIT_MAX_RETRY_DELAY_MS = 24 * 60 * 60 * 1000L
    }

    private val logger = LoggerFactory.getLogger(javaClass)
    private val cache = ConcurrentHashMap<String, NotificationConfigCacheEntry>()
    private val cacheLock = Any()
    private val initStarted = AtomicBoolean(false)
    private val initLastException = AtomicReference<Exception?>(null)

    @PostConstruct
    fun init() {
        if (!initStarted.compareAndSet(false, true)) {
            return
        }

        scheduleInitAttempt(attempt = 1, delayMs = 0)
    }

    private fun scheduleInitAttempt(attempt: Int, delayMs: Long) {
        scheduleRetry(delayMs) {
            try {
                refreshAll()
                logger.debug("Notification config cache warmup completed")
            } catch (ex: Exception) {
                initLastException.set(ex)

                if (attempt >= INIT_MAX_RETRIES) {
                    val initializationError =
                        IllegalStateException(
                            "Failed to initialize NotificationConfigCache after $INIT_MAX_RETRIES attempts",
                            initLastException.get()
                        )
                    logger.error(initializationError.message, initializationError)
                    return@scheduleRetry
                }

                val retryDelayMs = calculateBackoffDelayMs(attempt)
                logger.warn(
                    "Failed to load notification configs on startup (attempt {}/{}). Retrying in {} ms: {}",
                    attempt,
                    INIT_MAX_RETRIES,
                    retryDelayMs,
                    ex.message
                )

                scheduleInitAttempt(attempt = attempt + 1, delayMs = retryDelayMs)
            }
        }
    }

    private fun calculateBackoffDelayMs(attempt: Int): Long {
        val multiplier = 1L shl (attempt - 1)
        return (INIT_INITIAL_RETRY_DELAY_MS * multiplier).coerceAtMost(INIT_MAX_RETRY_DELAY_MS)
    }

    override fun findAll(): List<NotificationConfigCacheEntry> =
        synchronized(cacheLock) {
            cache.values.toList()
        }

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

        synchronized(cacheLock) {
            cache.clear()
            cache.putAll(nextCache)
        }

        logger.debug("Loaded {} notification configs into memory cache", cache.size)
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
