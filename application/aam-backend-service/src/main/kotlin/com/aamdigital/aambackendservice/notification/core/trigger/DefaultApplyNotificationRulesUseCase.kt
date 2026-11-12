package com.aamdigital.aambackendservice.notification.core.trigger

import com.aamdigital.aambackendservice.common.changes.DocumentChangeEvent
import com.aamdigital.aambackendservice.common.condition.DocumentConditionEngine
import com.aamdigital.aambackendservice.common.condition.InvalidDocumentConditionsException
import com.aamdigital.aambackendservice.common.domain.ApplicationConfig
import com.aamdigital.aambackendservice.common.domain.UseCaseOutcome
import com.aamdigital.aambackendservice.common.permission.core.PermissionCheckClient
import com.aamdigital.aambackendservice.notification.core.CreateUserNotificationEvent
import com.aamdigital.aambackendservice.notification.core.config.NotificationConfigCache
import com.aamdigital.aambackendservice.notification.core.config.NotificationConfigCacheEntry
import com.aamdigital.aambackendservice.notification.core.config.NotificationRuleCacheEntry
import com.aamdigital.aambackendservice.notification.core.outbox.UserNotificationPublisher
import com.aamdigital.aambackendservice.notification.domain.EntityNotificationContext
import com.aamdigital.aambackendservice.notification.domain.NotificationChannelType
import com.aamdigital.aambackendservice.notification.domain.NotificationDetails
import org.springframework.web.util.UriComponentsBuilder
import java.nio.charset.StandardCharsets
import java.util.UUID

/** Applies persisted notification rules to a document change event and publishes matching notifications. */
class DefaultApplyNotificationRulesUseCase(
    private val notificationConfigCaches: List<NotificationConfigCache>,
    private val userNotificationPublisher: UserNotificationPublisher,
    private val permissionCheckClient: PermissionCheckClient,
    private val applicationConfig: ApplicationConfig,
    private val emailEnabled: Boolean,
    private val pushEnabled: Boolean,
    private val documentConditionEngine: DocumentConditionEngine = DocumentConditionEngine()
) : ApplyNotificationRulesUseCase() {
    override fun apply(request: ApplyNotificationRulesRequest): UseCaseOutcome<ApplyNotificationRulesData> {
        val changedEntity = request.documentChangeEvent.entityType
        val changeType = request.documentChangeEvent.changeType

        val notificationConfigurations = notificationConfigCaches.flatMap { it.findAll() }

        logger.trace(
            "Processing change: entity={}, changeType={}, configCount={}",
            changedEntity,
            changeType,
            notificationConfigurations.size
        )

        val matchedRules =
            prefilterRules(notificationConfigurations, changedEntity, changeType)
                .filter { (notificationConfig, rule) ->
                    logger.trace("{} -> {}", notificationConfig.userIdentifier, rule)
                    matchesConditions(rule, request.documentChangeEvent)
                }

        if (matchedRules.isEmpty()) {
            logger.trace(
                "No matching notification rules for entity={}, changeType={}",
                changedEntity,
                changeType
            )
            return UseCaseOutcome.Success(ApplyNotificationRulesData(0))
        }

        logger.trace(
            "Found {} matched notification rules for entity={}, changeType={}",
            matchedRules.size,
            changedEntity,
            changeType
        )

        // the permission check only applies to users; an external recipient gets what the form's admin configured
        val (externalRules, userRules) = matchedRules.partition { (config, _) -> config.isExternalRecipient }

        val permissionMap =
            if (userRules.isEmpty()) {
                emptyMap()
            } else {
                permissionCheckClient.checkPermissions(
                    userIds = userRules.map { it.first.userIdentifier }.distinct(),
                    entityId = request.documentChangeEvent.documentId,
                    action = "read"
                )
            }

        logger.trace(
            "Permission check results: {}",
            permissionMap
        )

        val triggeredEvents =
            userRules.mapNotNull { (notificationConfig, rule) ->
                val hasPermission = permissionMap[notificationConfig.userIdentifier] == true
                if (!hasPermission) {
                    logger.trace(
                        "Skipped notification event due to missing permissions: user={}, rule={}, entityId={}",
                        notificationConfig.userIdentifier,
                        rule.externalIdentifier,
                        request.documentChangeEvent.documentId
                    )
                    return@mapNotNull null
                }

                publishNotificationEventForUser(
                    notificationConfig,
                    rule,
                    request.documentChangeEvent
                )
            } +
                externalRules.mapNotNull { (notificationConfig, rule) ->
                    publishNotificationEventForExternalRecipient(
                        notificationConfig,
                        rule,
                        request.documentChangeEvent
                    )
                }

        return UseCaseOutcome.Success(
            ApplyNotificationRulesData(
                triggeredEvents.size
            )
        )
    }

    /**
     * Do a simple filtering based on entity type and change type to avoid unnecessary rule evaluation
     * and flatten the list of rules for further processing.
     */
    private fun prefilterRules(
        notificationConfigurations: List<NotificationConfigCacheEntry>,
        changedEntity: String,
        changeType: String
    ): List<Pair<NotificationConfigCacheEntry, NotificationRuleCacheEntry>> =
        notificationConfigurations.flatMap { notificationConfig ->
            notificationConfig.rules
                .filter { it.enabled && it.entityType == changedEntity && it.changeType == changeType }
                .map { rule -> Pair(notificationConfig, rule) }
        }

    /**
     * A rule whose conditions turn out to be invalid does not match, so that it cannot keep the
     * other rules from being applied to this change.
     */
    private fun matchesConditions(
        rule: NotificationRuleCacheEntry,
        documentChangeEvent: DocumentChangeEvent
    ): Boolean =
        try {
            documentConditionEngine.matches(
                conditions = rule.conditions,
                document = documentChangeEvent.currentVersion
            )
        } catch (ex: InvalidDocumentConditionsException) {
            logger.warn(
                "Skipping notification rule with invalid conditions: rule={}, entityId={}",
                rule.externalIdentifier,
                documentChangeEvent.documentId,
                ex
            )
            false
        }

    private fun publishNotificationEventForUser(
        notificationConfig: NotificationConfigCacheEntry,
        rule: NotificationRuleCacheEntry,
        documentChangeEvent: DocumentChangeEvent
    ): NotificationDetails {
        val baseDetails =
            NotificationDetails(
                // Derived from the change that caused it, so reprocessing the same document
                // revision produces the same notification instead of a second copy. The in-app
                // document is keyed on this id, and the outbox entry on this id plus the channel.
                id =
                    notificationIdFor(
                        documentChangeEvent = documentChangeEvent,
                        userIdentifier = notificationConfig.userIdentifier,
                        ruleIdentifier = rule.externalIdentifier
                    ),
                title = rule.label,
                context =
                    EntityNotificationContext(
                        entityType = rule.entityType,
                        entityId = documentChangeEvent.documentId
                    ),
                notificationType = rule.notificationType
            )

        val actionUrl = buildActionUrl(baseDetails)
        val notificationDetails = baseDetails.copy(actionUrl = actionUrl)

        val channelTypes = mutableListOf(NotificationChannelType.APP)

        // notificationConfig.channelPush is currently ignored
        // because we don't have a global push registration for users, but only device-level registrations.
        // the consumer only sends notification out to whatever devices are registered.
        //
        // Only emit a channel when a handler for it can exist, the same guard the email channel has:
        // a channel with no handler produces notifications that can never be delivered and are
        // retried on every restart.
        if (pushEnabled) {
            channelTypes.add(NotificationChannelType.PUSH)
        }
        if (emailEnabled && notificationConfig.channelEmail) {
            channelTypes.add(NotificationChannelType.EMAIL)
        }

        channelTypes.forEach { channelType ->
            userNotificationPublisher.publish(
                CreateUserNotificationEvent(
                    userIdentifier = notificationConfig.userIdentifier,
                    notificationChannelType = channelType,
                    notificationRule = rule.externalIdentifier,
                    details = notificationDetails
                )
            )
        }

        return notificationDetails
    }

    /**
     * Email only: there is no account for an in-app notification or a registered device.
     * Sends nothing when the record has no address, so no undeliverable entry reaches the outbox.
     */
    private fun publishNotificationEventForExternalRecipient(
        notificationConfig: NotificationConfigCacheEntry,
        rule: NotificationRuleCacheEntry,
        documentChangeEvent: DocumentChangeEvent
    ): NotificationDetails? {
        val emailField = notificationConfig.recipientEmailField ?: return null
        val recipientEmail =
            (fieldValue(documentChangeEvent.currentVersion, emailField) as? String)
                ?.trim()
                ?.takeIf { it.isNotEmpty() }

        if (recipientEmail == null) {
            logger.info(
                "No address in field '{}' of {}, so no submission confirmation is sent",
                emailField,
                documentChangeEvent.documentId
            )
            return null
        }

        if (!emailEnabled) {
            logger.warn(
                "Email is not configured, so the submission confirmation for {} cannot be sent",
                documentChangeEvent.documentId
            )
            return null
        }

        val notificationDetails =
            NotificationDetails(
                id =
                    notificationIdFor(
                        documentChangeEvent = documentChangeEvent,
                        userIdentifier = notificationConfig.userIdentifier,
                        ruleIdentifier = rule.externalIdentifier
                    ),
                title = rule.label,
                context =
                    EntityNotificationContext(
                        entityType = rule.entityType,
                        entityId = documentChangeEvent.documentId
                    ),
                notificationType = rule.notificationType
            )

        userNotificationPublisher.publish(
            CreateUserNotificationEvent(
                // the form the rule came from; keeps the notification id stable while the entry is in the outbox
                userIdentifier = notificationConfig.userIdentifier,
                notificationChannelType = NotificationChannelType.EMAIL,
                notificationRule = rule.externalIdentifier,
                details = notificationDetails,
                recipientEmail = recipientEmail
            )
        )

        return notificationDetails
    }

    /** follows dotted paths into nested objects, like the condition engine */
    private fun fieldValue(
        document: Map<*, *>,
        fieldPath: String
    ): Any? = fieldPath.split(".").fold<String, Any?>(document) { current, key -> (current as? Map<*, *>)?.get(key) }

    /**
     * Stable notification id for one (document revision, user, rule) combination.
     *
     * Uses a name-based UUID so that reprocessing a change - after a restart, or because the change
     * cursor did not advance - re-derives the same id and delivery becomes a no-op instead of
     * sending the notification twice.
     */
    private fun notificationIdFor(
        documentChangeEvent: DocumentChangeEvent,
        userIdentifier: String,
        ruleIdentifier: String
    ): UUID =
        UUID.nameUUIDFromBytes(
            listOf(
                documentChangeEvent.documentId,
                documentChangeEvent.rev,
                userIdentifier,
                ruleIdentifier
            ).joinToString("|").toByteArray(StandardCharsets.UTF_8)
        )

    private fun buildActionUrl(details: NotificationDetails): String =
        UriComponentsBuilder
            .fromUriString(applicationConfig.normalizedBaseUrl)
            .pathSegment("notification", "{id}")
            .buildAndExpand(mapOf("id" to details.id))
            .toUriString()
}
