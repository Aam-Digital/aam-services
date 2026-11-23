package com.aamdigital.aambackendservice.notification.core.config

import com.aamdigital.aambackendservice.common.condition.DocumentConditionEngine
import com.aamdigital.aambackendservice.common.couchdb.core.CouchDbClient
import com.aamdigital.aambackendservice.common.domain.TranslatableText
import com.aamdigital.aambackendservice.notification.domain.NotificationType
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
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
    couchDbClient: CouchDbClient,
    private val objectMapper: ObjectMapper,
    ttl: Duration? = null,
    clock: Clock = Clock.systemUTC(),
    /** the language the notification emails are written in, so a multilingual form title matches them */
    private val locale: String = "en",
    private val documentConditionEngine: DocumentConditionEngine = DocumentConditionEngine()
) : DocumentBackedNotificationConfigCache<PublicFormConfigDto>(
        couchDbClient = couchDbClient,
        objectMapper = objectMapper,
        documentType = DOCUMENT_PREFIX,
        documentClass = PublicFormConfigDto::class,
        ttl = ttl,
        clock = clock
    ) {
    companion object {
        private const val DOCUMENT_PREFIX = "PublicFormConfig"

        /** How the frontend marks a record created through a public form. */
        private const val CREATED_BY_PREFIX = "PublicForm:"
        private const val CREATED_BY_FIELD = "created.by"

        /** A confirmation is owed for the submission itself, not for later edits of the record. */
        private const val CHANGE_TYPE_CREATED = "created"
    }

    /** `null` when this form does not confirm submissions, or cannot say where to send them. */
    override fun toCacheEntry(document: PublicFormConfigDto): NotificationConfigCacheEntry? {
        if (document.sendConfirmationEmail != true) {
            return null
        }

        val formIdentifier =
            extractIdentifier(document.id)
                ?: throw IllegalArgumentException("Invalid PublicFormConfig ID format: ${document.id}")
        val emailField = document.confirmationEmailField?.takeIf { it.isNotBlank() }
        val recordType = document.entity?.takeIf { it.isNotBlank() }

        if (emailField == null || recordType == null) {
            // half-configured in the admin UI: say so once here rather than silently never sending
            logger.warn(
                "Public form {} asks for a submission confirmation but has no {}, so none is sent",
                document.id,
                if (emailField == null) "email field configured" else "record type configured"
            )
            return null
        }

        return NotificationConfigCacheEntry(
            userIdentifier = formIdentifier,
            channelPush = false,
            channelEmail = true,
            recipientEmailField = emailField,
            confirmationSubject = resolveText(document.confirmationEmailSubject),
            confirmationBody = resolveText(document.confirmationEmailBody),
            rules = listOf(buildRule(formIdentifier, recordType, document.title))
        )
    }

    private fun buildRule(
        formIdentifier: String,
        recordType: String,
        title: JsonNode?
    ): NotificationRuleCacheEntry {
        val conditionNode =
            objectMapper.createObjectNode().put(CREATED_BY_FIELD, CREATED_BY_PREFIX + formIdentifier)

        return NotificationRuleCacheEntry(
            // the form's own title; the wording around it is added when the email is written, so
            // that it is translated along with the rest of the email
            label = resolveText(title).orEmpty(),
            externalIdentifier = buildExternalIdentifier(formIdentifier, recordType),
            notificationType = NotificationType.ENTITY_CHANGE,
            entityType = recordType,
            changeType = CHANGE_TYPE_CREATED,
            conditions = documentConditionEngine.parse(conditionNode),
            enabled = true
        )
    }

    /** Language codes only, so `de-DE` matches a `de` key, like the template lookup does. */
    private fun resolveText(text: JsonNode?): String? {
        if (text?.isObject == true) {
            text
                .properties()
                .firstOrNull {
                    languageOf(it.key) == languageOf(locale) &&
                        it.value.isTextual &&
                        it.value.textValue().isNotBlank()
                }?.let { return it.value.textValue().trim() }
        }
        return TranslatableText.resolve(text)?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun languageOf(locale: String): String =
        locale
            .substringBefore('-')
            .substringBefore('_')
            .trim()
            .lowercase()

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
}
