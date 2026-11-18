package com.aamdigital.aambackendservice.notification.core.config

import com.aamdigital.aambackendservice.common.cache.LazySnapshot
import com.aamdigital.aambackendservice.common.condition.DocumentConditionEngine
import com.aamdigital.aambackendservice.common.condition.DocumentConditions
import com.aamdigital.aambackendservice.common.condition.InvalidDocumentConditionsException
import com.aamdigital.aambackendservice.common.couchdb.core.CouchDbClient
import com.aamdigital.aambackendservice.common.couchdb.core.getEmptyQueryParams
import com.aamdigital.aambackendservice.common.error.NotFoundException
import com.aamdigital.aambackendservice.common.placeholder.core.PlaceholderResolutionException
import com.aamdigital.aambackendservice.common.placeholder.core.PlaceholderResolver
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Duration
import java.util.UUID

/**
 * The notification rules each user configured for themselves, from their `NotificationConfig:*`.
 *
 * Placeholders in a rule's conditions (e.g. `$current_user`) are resolved once, when the config is
 * loaded. Their values (e.g. the entity linked to a user account) can change without the document
 * changing, which is why the whole cache is loaded again once it is older than `ttl`.
 *
 * A rule whose placeholders cannot be resolved (no entity linked to the account, or Keycloak is
 * unreachable) is skipped with a warning, without affecting the user's other rules. Disabled rules
 * are left out, so their placeholders are not looked up either.
 */
class DefaultNotificationConfigCache(
    couchDbClient: CouchDbClient,
    objectMapper: ObjectMapper,
    private val placeholderResolver: PlaceholderResolver = PlaceholderResolver(null),
    ttl: Duration? = null,
    clock: Clock = Clock.systemUTC(),
    private val documentConditionEngine: DocumentConditionEngine = DocumentConditionEngine()
) : DocumentBackedNotificationConfigCache<NotificationConfigDto>(
        couchDbClient = couchDbClient,
        objectMapper = objectMapper,
        documentType = DOCUMENT_PREFIX,
        documentClass = NotificationConfigDto::class,
        ttl = ttl,
        clock = clock
    ) {
    companion object {
        private const val DOCUMENT_PREFIX = "NotificationConfig"
    }

    override fun toCacheEntry(document: NotificationConfigDto): NotificationConfigCacheEntry {
        val userIdentifier =
            extractIdentifier(document.id)
                ?: throw IllegalArgumentException("Invalid NotificationConfig ID format: ${document.id}")

        return NotificationConfigCacheEntry(
            userIdentifier = userIdentifier,
            channelPush = document.channels?.push ?: false,
            channelEmail = document.channels?.email ?: false,
            rules = mapToNotificationRules(document, userIdentifier)
        )
    }

    private fun mapToNotificationRules(
        notificationConfig: NotificationConfigDto,
        userIdentifier: String
    ): List<NotificationRuleCacheEntry> =
        notificationConfig.notificationRules
            .withIndex()
            .filter { (_, rule) -> rule.enabled }
            .flatMap { (ruleIndex, rule) ->
                val conditions =
                    try {
                        // the rule identifier is derived from the resolved conditions, so it is the same
                        // whether the frontend or the backend resolved the placeholders
                        documentConditionEngine.parse(placeholderResolver.resolve(rule.conditions, userIdentifier))
                    } catch (ex: InvalidDocumentConditionsException) {
                        // skip only this rule, so one broken rule does not disable the user's others
                        logger.warn(
                            "Skipping notification rule with invalid conditions: notificationConfigId={}, ruleIndex={}",
                            notificationConfig.id,
                            ruleIndex,
                            ex
                        )
                        return@flatMap emptyList()
                    } catch (ex: PlaceholderResolutionException) {
                        // e.g. no entity linked to the account, or Keycloak unreachable: skip only this rule
                        logger.warn(
                            "Skipping notification rule whose placeholders cannot be resolved: " +
                                "notificationConfigId={}, ruleIndex={}",
                            notificationConfig.id,
                            ruleIndex,
                            ex
                        )
                        return@flatMap emptyList()
                    }

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
