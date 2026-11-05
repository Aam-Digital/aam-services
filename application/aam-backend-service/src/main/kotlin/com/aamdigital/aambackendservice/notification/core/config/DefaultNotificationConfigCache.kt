package com.aamdigital.aambackendservice.notification.core.config

import com.aamdigital.aambackendservice.common.cache.LazySnapshot
import com.aamdigital.aambackendservice.common.condition.DocumentConditionEngine
import com.aamdigital.aambackendservice.common.condition.DocumentConditions
import com.aamdigital.aambackendservice.common.condition.InvalidDocumentConditionsException
import com.aamdigital.aambackendservice.common.couchdb.core.CouchDbClient
import com.aamdigital.aambackendservice.common.couchdb.core.getEmptyQueryParams
import com.aamdigital.aambackendservice.common.error.NotFoundException
import com.aamdigital.aambackendservice.common.placeholder.core.PlaceholderResolver
import com.aamdigital.aambackendservice.common.placeholder.core.PlaceholderValueLookupException
import com.aamdigital.aambackendservice.common.placeholder.core.PlaceholderValueProvider
import com.aamdigital.aambackendservice.common.placeholder.core.UnresolvedPlaceholderException
import com.aamdigital.aambackendservice.common.placeholder.core.UserPlaceholderValueProvider
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Duration
import java.util.UUID

/**
 * CouchDB-backed in-memory implementation of [NotificationConfigCache].
 *
 * All `NotificationConfig:*` documents are loaded on first use (see [LazySnapshot]), and one entry
 * is updated whenever a matching document change is handled. Loading lazily rather than warming up
 * at startup means the first change is never matched against a cache that has not finished loading
 * - which used to drop that change's notifications silently - and a failed load throws, so it is
 * logged as a failed change instead of looking like "no rule matched".
 *
 * Placeholders in a rule's conditions (e.g. `$current_user`, see
 * [com.aamdigital.aambackendservice.common.placeholder.core.Placeholders]) are resolved once, when
 * the config is loaded into the cache, with values from [placeholderValueProvider]. Those values
 * (e.g. the entity linked to a user account) can change without the config document changing, so the
 * whole cache is loaded again once it is older than [ttl]. If that reload fails (e.g. Keycloak is
 * unreachable), the previous copy is used and the reload is tried again after
 * [RELOAD_RETRY_INTERVAL], so that an outage does not stop all notifications.
 *
 * Disabled rules are left out: they never trigger a notification, so their placeholders are not
 * looked up either.
 *
 * @param ttl how long the loaded configs are used before they are loaded again; `null` keeps them
 *     until the service restarts (they are still refreshed on every config document change)
 */
class DefaultNotificationConfigCache(
    private val couchDbClient: CouchDbClient,
    private val objectMapper: ObjectMapper,
    private val placeholderValueProvider: PlaceholderValueProvider = UserPlaceholderValueProvider(null),
    ttl: Duration? = null,
    clock: Clock = Clock.systemUTC(),
    private val documentConditionEngine: DocumentConditionEngine = DocumentConditionEngine()
) : NotificationConfigCache {
    companion object {
        private const val DATABASE = "app"
        private const val DOCUMENT_PREFIX = "NotificationConfig"

        /** How long the previous copy is used after a failed reload before trying again. */
        val RELOAD_RETRY_INTERVAL: Duration = Duration.ofMinutes(5)
    }

    private val logger = LoggerFactory.getLogger(javaClass)

    private val placeholderResolver = PlaceholderResolver(objectMapper)

    /** Cache entries by user identifier. */
    private val configs = LazySnapshot(ttl, clock, RELOAD_RETRY_INTERVAL) { loadConfigs() }

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
            } catch (ex: PlaceholderValueLookupException) {
                // a temporary failure: keep the previous version rather than dropping the user's rules
                logger.error(
                    "Could not resolve placeholders during refresh, keeping the cached version: " +
                        "notificationConfigId={}, userIdentifier={}",
                    notificationConfigId,
                    userIdentifier,
                    ex
                )
                return
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
        } catch (ex: PlaceholderValueLookupException) {
            // a temporary failure: fail the whole load so it is retried, instead of dropping the config
            throw ex
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
            rules = mapToNotificationRules(notificationConfig, userIdentifier)
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

    private fun mapToNotificationRules(
        notificationConfig: NotificationConfigDto,
        userIdentifier: String
    ): List<NotificationRuleCacheEntry> {
        // keep the index of each rule in the document, as the rule identifier is derived from it
        val enabledRules = notificationConfig.notificationRules.withIndex().filter { (_, rule) -> rule.enabled }
        val placeholderValues = lookupPlaceholderValues(enabledRules.map { it.value }, userIdentifier)

        return enabledRules.flatMap { (ruleIndex, rule) ->
            val conditions =
                parseConditions(notificationConfig.id, ruleIndex, rule.conditions, placeholderValues)
                    ?: return@flatMap emptyList()

            rule.changeType.map { changeType ->
                val externalIdentifierInput =
                    ExternalIdentifierInput(
                        notificationConfigId = notificationConfig.id,
                        ruleIndex = ruleIndex,
                        label = rule.label,
                        entityType = rule.entityType,
                        changeType = changeType,
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

    /** Looks up the values of all placeholders the [rules] use, at most once per config. */
    private fun lookupPlaceholderValues(
        rules: List<NotificationRuleDto>,
        userIdentifier: String
    ): Map<String, Any> {
        val usedPlaceholders =
            rules
                .flatMap { rule -> placeholderResolver.findPlaceholders(rule.conditions) }
                .toSet()
        if (usedPlaceholders.isEmpty()) {
            return emptyMap()
        }
        return placeholderValueProvider.getValues(userIdentifier, usedPlaceholders)
    }

    /**
     * The rule's conditions with placeholders resolved, or `null` to skip only this rule, so that one
     * broken rule does not disable the user's others.
     *
     * The rule identifier is derived from the resolved conditions, so it is the same whether the
     * frontend or the backend resolved the placeholders.
     */
    private fun parseConditions(
        notificationConfigId: String,
        ruleIndex: Int,
        conditions: JsonNode,
        placeholderValues: Map<String, Any>
    ): DocumentConditions? =
        try {
            documentConditionEngine.parse(placeholderResolver.resolve(conditions, placeholderValues))
        } catch (
            @Suppress("SwallowedException") ex: UnresolvedPlaceholderException
        ) {
            logger.warn(
                "Skipping notification rule with a placeholder that has no value for this user: " +
                    "notificationConfigId={}, ruleIndex={}, placeholder={}",
                notificationConfigId,
                ruleIndex,
                ex.placeholder
            )
            null
        } catch (ex: InvalidDocumentConditionsException) {
            logger.warn(
                "Skipping notification rule with invalid conditions: notificationConfigId={}, ruleIndex={}",
                notificationConfigId,
                ruleIndex,
                ex
            )
            null
        }

    private data class ExternalIdentifierInput(
        val notificationConfigId: String,
        val ruleIndex: Int,
        val label: String,
        val entityType: String,
        val changeType: String,
        val conditions: DocumentConditions
    )

    private fun buildExternalIdentifier(input: ExternalIdentifierInput): String {
        val stableIdentifier =
            listOf(
                input.notificationConfigId,
                input.ruleIndex.toString(),
                input.label,
                input.entityType,
                input.changeType,
                input.conditions.canonicalJson
            ).joinToString("#")

        return UUID.nameUUIDFromBytes(stableIdentifier.toByteArray(StandardCharsets.UTF_8)).toString()
    }
}
