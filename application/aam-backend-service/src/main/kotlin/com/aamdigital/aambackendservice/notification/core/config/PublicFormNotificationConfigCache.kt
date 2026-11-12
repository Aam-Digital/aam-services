package com.aamdigital.aambackendservice.notification.core.config

import com.aamdigital.aambackendservice.common.cache.LazySnapshot
import com.aamdigital.aambackendservice.common.condition.DocumentConditionEngine
import com.aamdigital.aambackendservice.common.couchdb.core.CouchDbClient
import com.aamdigital.aambackendservice.common.couchdb.core.getEmptyQueryParams
import com.aamdigital.aambackendservice.common.error.NotFoundException
import com.aamdigital.aambackendservice.notification.domain.NotificationType
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Duration
import java.util.UUID

/**
 * One notification rule per public form that confirms submissions by email: it matches the records
 * that form creates (`created.by = "PublicForm:<id>"`, written by the frontend on submit) and the
 * address is taken from the submitted record.
 */
class PublicFormNotificationConfigCache(
    private val couchDbClient: CouchDbClient,
    private val objectMapper: ObjectMapper,
    ttl: Duration? = null,
    clock: Clock = Clock.systemUTC(),
    private val documentConditionEngine: DocumentConditionEngine = DocumentConditionEngine()
) : NotificationConfigCache {
    companion object {
        private const val DATABASE = "app"
        private const val DOCUMENT_PREFIX = "PublicFormConfig"

        /** How the frontend marks a record created through a public form. */
        private const val CREATED_BY_PREFIX = "PublicForm:"
        private const val CREATED_BY_FIELD = "created.by"

        /** A confirmation is owed for the submission itself, not for later edits of the record. */
        private const val CHANGE_TYPE_CREATED = "created"

        private const val LABEL = "Submission received"
        private const val DEFAULT_LANGUAGE = "en"

        val RELOAD_RETRY_INTERVAL: Duration = Duration.ofMinutes(5)
    }

    private val logger = LoggerFactory.getLogger(javaClass)

    override val sourceEntityType: String = DOCUMENT_PREFIX

    /** Cache entries by public form identifier. */
    private val configs = LazySnapshot(ttl, clock, RELOAD_RETRY_INTERVAL) { loadConfigs() }

    override fun findAll(): List<NotificationConfigCacheEntry> = configs.get().values.toList()

    override fun refreshConfig(
        database: String,
        notificationConfigId: String,
        deleted: Boolean
    ) {
        val formIdentifier = extractFormIdentifier(notificationConfigId) ?: return

        if (deleted) {
            configs.update { it - formIdentifier }
            logger.debug("Removed public form from notification cache: {}", notificationConfigId)
            return
        }

        val publicFormConfig =
            try {
                couchDbClient.getDatabaseDocument(
                    database = database,
                    documentId = notificationConfigId,
                    queryParams = getEmptyQueryParams(),
                    kClass = PublicFormConfigDto::class
                )
            } catch (
                @Suppress("SwallowedException") ex: NotFoundException
            ) {
                configs.update { it - formIdentifier }
                logger.debug("Public form not found during refresh, removed from cache: {}", notificationConfigId)
                return
            }

        val entry =
            try {
                toCacheEntry(publicFormConfig)
            } catch (ex: Exception) {
                configs.update { it - formIdentifier }
                logger.error("Skipping invalid PublicFormConfig during refresh: {}", notificationConfigId, ex)
                return
            }

        // a form that no longer confirms submissions must stop matching, not keep its old rule
        if (entry == null) {
            configs.update { it - formIdentifier }
            logger.debug("Public form no longer confirms submissions, removed from cache: {}", notificationConfigId)
            return
        }

        configs.update { it + (formIdentifier to entry) }
        logger.debug("Refreshed public form in notification cache: {}", notificationConfigId)
    }

    private fun loadConfigs(): Map<String, NotificationConfigCacheEntry> =
        couchDbClient
            .getDatabaseDocumentsByPrefix(
                database = DATABASE,
                prefix = DOCUMENT_PREFIX,
                kClass = ObjectNode::class
            ).mapNotNull { doc -> parseConfigFromDoc(doc) }
            .associateBy { it.userIdentifier }
            .also { loaded -> logger.debug("Loaded {} public form confirmations into memory cache", loaded.size) }

    private fun parseConfigFromDoc(doc: ObjectNode): NotificationConfigCacheEntry? =
        try {
            toCacheEntry(objectMapper.convertValue(doc, PublicFormConfigDto::class.java))
        } catch (ex: Exception) {
            logger.warn("Skipping invalid PublicFormConfig document while loading the cache", ex)
            null
        }

    /** `null` when this form does not confirm submissions, or cannot say where to send them. */
    private fun toCacheEntry(publicFormConfig: PublicFormConfigDto): NotificationConfigCacheEntry? {
        if (publicFormConfig.sendConfirmationEmail != true) {
            return null
        }

        val formIdentifier =
            extractFormIdentifier(publicFormConfig.id)
                ?: throw IllegalArgumentException("Invalid PublicFormConfig ID format: ${publicFormConfig.id}")
        val emailField = publicFormConfig.confirmationEmailField?.takeIf { it.isNotBlank() }
        val recordType = publicFormConfig.entity?.takeIf { it.isNotBlank() }

        if (emailField == null || recordType == null) {
            // half-configured in the admin UI: say so once here rather than silently never sending
            logger.warn(
                "Public form {} asks for a submission confirmation but has no {}, so none is sent",
                publicFormConfig.id,
                if (emailField == null) "email field configured" else "record type configured"
            )
            return null
        }

        return NotificationConfigCacheEntry(
            userIdentifier = formIdentifier,
            channelPush = false,
            channelEmail = true,
            recipientEmailField = emailField,
            rules = listOf(buildRule(formIdentifier, recordType, publicFormConfig.title))
        )
    }

    private fun buildRule(
        formIdentifier: String,
        recordType: String,
        title: JsonNode?
    ): NotificationRuleCacheEntry {
        val conditionNode =
            objectMapper.createObjectNode().put(CREATED_BY_FIELD, CREATED_BY_PREFIX + formIdentifier)

        // shown as the email subject and heading
        val formTitle = resolveTitle(title)
        val label = if (formTitle != null) "$LABEL: $formTitle" else LABEL

        return NotificationRuleCacheEntry(
            label = label,
            externalIdentifier = buildExternalIdentifier(formIdentifier, recordType),
            notificationType = NotificationType.ENTITY_CHANGE,
            entityType = recordType,
            changeType = CHANGE_TYPE_CREATED,
            conditions = documentConditionEngine.parse(conditionNode),
            enabled = true
        )
    }

    /** a plain string, or the default language of a multilingual title */
    private fun resolveTitle(title: JsonNode?): String? {
        val text =
            when {
                title == null || title.isNull -> {
                    null
                }

                title.isTextual -> {
                    title.asText()
                }

                title.isObject -> {
                    title.get(DEFAULT_LANGUAGE)?.takeIf { it.isTextual }?.asText()
                        ?: title
                            .elements()
                            .asSequence()
                            .firstOrNull { it.isTextual }
                            ?.asText()
                }

                else -> {
                    null
                }
            }
        return text?.trim()?.takeIf { it.isNotEmpty() }
    }

    /** Stable across restarts, so that a replayed change re-derives the same notification id. */
    private fun buildExternalIdentifier(
        formIdentifier: String,
        recordType: String
    ): String =
        UUID
            .nameUUIDFromBytes(
                listOf(DOCUMENT_PREFIX, formIdentifier, recordType, CHANGE_TYPE_CREATED)
                    .joinToString("#")
                    .toByteArray(StandardCharsets.UTF_8)
            ).toString()

    private fun extractFormIdentifier(publicFormConfigId: String): String? {
        val parts = publicFormConfigId.split(":", limit = 2)
        if (parts.size != 2 || parts.first() != DOCUMENT_PREFIX || parts[1].isBlank()) {
            logger.warn("Invalid PublicFormConfig ID format: {}", publicFormConfigId)
            return null
        }
        return parts[1]
    }
}
