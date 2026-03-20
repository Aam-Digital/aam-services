package com.aamdigital.aambackendservice.notification.core.trigger

import com.aamdigital.aambackendservice.common.changes.DocumentChangeEvent
import com.aamdigital.aambackendservice.common.condition.DocumentConditionEngine
import com.aamdigital.aambackendservice.common.domain.UseCaseOutcome
import com.aamdigital.aambackendservice.notification.core.config.NotificationConfigCache
import com.aamdigital.aambackendservice.notification.core.config.NotificationConfigCacheEntry
import com.aamdigital.aambackendservice.notification.core.config.NotificationRuleCacheEntry
import com.aamdigital.aambackendservice.notification.core.CreateUserNotificationEvent
import com.aamdigital.aambackendservice.notification.di.NotificationQueueConfiguration.Companion.USER_NOTIFICATION_QUEUE
import com.aamdigital.aambackendservice.notification.domain.EntityNotificationContext
import com.aamdigital.aambackendservice.notification.domain.NotificationChannelType
import com.aamdigital.aambackendservice.notification.domain.NotificationDetails
import com.aamdigital.aambackendservice.notification.queue.UserNotificationPublisher

/** Applies persisted notification rules to a document change event and publishes matching notifications. */
class DefaultApplyNotificationRulesUseCase(
    private val notificationConfigCache: NotificationConfigCache,
    private val userNotificationPublisher: UserNotificationPublisher,
    private val documentConditionEngine: DocumentConditionEngine = DocumentConditionEngine()
) : ApplyNotificationRulesUseCase() {
    override fun apply(request: ApplyNotificationRulesRequest): UseCaseOutcome<ApplyNotificationRulesData> {
        val changedEntity =
            request.documentChangeEvent.documentId
                .split(":")
                .first()
        val changeType = extractChangeType(request.documentChangeEvent)

        val notificationConfigurations = notificationConfigCache.findAll()

        val triggeredEvents =
            prefilterRules(notificationConfigurations, changedEntity, changeType)
                .filter { (notificationConfig, rule) ->
                    logger.trace("{} -> {}", notificationConfig.userIdentifier, rule)
                    documentConditionEngine.matchesAll(
                        conditions = rule.conditions,
                        document = request.documentChangeEvent.currentVersion
                    )
                }.map { (notificationConfig, rule) ->
                    publishNotificationEventForUser(
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

    private fun extractChangeType(documentChangeEvent: DocumentChangeEvent): String {
        if (documentChangeEvent.deleted) {
            return "deleted"
        }

        // Parse CouchDB revision prefix to determine change type.
        // Revisions have format "<generation>-<hash>" where generation 1 = created, 2+ = updated.
        // This is more reliable than checking previousVersion which may be empty even for updates
        // (e.g. when the previous revision has been purged).
        val generation = documentChangeEvent.rev.substringBefore("-").toIntOrNull()
        if (generation != null && generation > 1) {
            return "updated"
        }

        return "created"
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

    private fun publishNotificationEventForUser(
        notificationConfig: NotificationConfigCacheEntry,
        rule: NotificationRuleCacheEntry,
        documentChangeEvent: DocumentChangeEvent
    ): NotificationDetails {
        val notificationDetails =
            NotificationDetails(
                title = rule.label,
                // body = "name and details of the entity ...", // we load this only in the frontend currently. Add here later if needed for other channels like email
                // TODO: add actionUrl for better linking
                context =
                    EntityNotificationContext(
                        entityType = rule.entityType,
                        entityId = documentChangeEvent.documentId
                    ),
                notificationType = rule.notificationType
            )

        userNotificationPublisher.publish(
            channel = USER_NOTIFICATION_QUEUE,
            event =
                CreateUserNotificationEvent(
                    userIdentifier = notificationConfig.userIdentifier,
                    notificationChannelType = NotificationChannelType.APP,
                    notificationRule = rule.externalIdentifier,
                    details = notificationDetails
                )
        )

//        todo refactor channelPush settings here
//        if (notificationConfig.channelPush) {
        userNotificationPublisher.publish(
            channel = USER_NOTIFICATION_QUEUE,
            event =
                CreateUserNotificationEvent(
                    userIdentifier = notificationConfig.userIdentifier,
                    notificationChannelType = NotificationChannelType.PUSH,
                    notificationRule = rule.externalIdentifier,
                    details = notificationDetails
                )
        )
//        }

        if (notificationConfig.channelEmail) {
            userNotificationPublisher.publish(
                channel = USER_NOTIFICATION_QUEUE,
                event =
                    CreateUserNotificationEvent(
                        userIdentifier = notificationConfig.userIdentifier,
                        notificationChannelType = NotificationChannelType.EMAIL,
                        notificationRule = rule.externalIdentifier,
                        details = notificationDetails
                    )
            )
        }

        return notificationDetails
    }

}
