package com.aamdigital.aambackendservice.notification.core.trigger

import com.aamdigital.aambackendservice.common.changes.domain.DocumentChangeEvent
import com.aamdigital.aambackendservice.common.domain.UseCaseOutcome
import com.aamdigital.aambackendservice.notification.core.CreateUserNotificationEvent
import com.aamdigital.aambackendservice.notification.di.NotificationQueueConfiguration.Companion.USER_NOTIFICATION_QUEUE
import com.aamdigital.aambackendservice.notification.domain.EntityNotificationContext
import com.aamdigital.aambackendservice.notification.domain.NotificationChannelType
import com.aamdigital.aambackendservice.notification.domain.NotificationDetails
import com.aamdigital.aambackendservice.notification.queue.UserNotificationPublisher
import com.aamdigital.aambackendservice.notification.repository.NotificationConditionEntity
import com.aamdigital.aambackendservice.notification.repository.NotificationConfigEntity
import com.aamdigital.aambackendservice.notification.repository.NotificationConfigRepository
import com.aamdigital.aambackendservice.notification.repository.NotificationRuleEntity
import org.apache.commons.lang3.StringUtils

class DefaultApplyNotificationRulesUseCase(
    val notificationConfigRepository: NotificationConfigRepository,
    val userNotificationPublisher: UserNotificationPublisher,
) : ApplyNotificationRulesUseCase() {

    override fun apply(request: ApplyNotificationRulesRequest): UseCaseOutcome<ApplyNotificationRulesData> {
        val changedEntity = request.documentChangeEvent.documentId.split(":").first()
        val changeType = extractChangeType(request.documentChangeEvent)

        val notificationConfigurations = notificationConfigRepository.findAll() // todo database access optimization

        val triggeredEvents = prefilterRules(notificationConfigurations, changedEntity, changeType)
            .filter { (notificationConfig, rule) ->
                logger.trace("{} -> {}", notificationConfig.userIdentifier, rule)
                rule.conditions.all { condition -> checkConditionForDocument(condition, request.documentChangeEvent) }
            }
            .map { (notificationConfig, rule) ->
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
            // "delete" is not relevant for active notifications currently (no rules for this will exist)
            return "deleted"
        }

        if (documentChangeEvent.previousVersion.isEmpty()) {
            return "created"
        }

        return "updated"
    }

    /**
     * Do a simple filtering based on entity type and change type to avoid unnecessary rule evaluation
     * and flatten the list of rules for further processing.
     */
    private fun prefilterRules(
        notificationConfigurations: MutableIterable<NotificationConfigEntity>,
        changedEntity: String,
        changeType: String,
    ): List<Pair<NotificationConfigEntity, NotificationRuleEntity>> {

        return notificationConfigurations.flatMap { notificationConfig ->
            notificationConfig.notificationRules
                .filter { it.enabled && it.entityType == changedEntity && it.changeType == changeType }
                .map { rule -> Pair(notificationConfig, rule) }
        }
    }

    private fun checkConditionForDocument(
        condition: NotificationConditionEntity,
        documentChangeEvent: DocumentChangeEvent
    ): Boolean {
        val conditionOutcome = _checkConditionForDocument(condition, documentChangeEvent)
        logger.trace("condition {} outcome {} for doc {}", condition, conditionOutcome, documentChangeEvent.documentId)
        return conditionOutcome
    }

    private fun publishNotificationEventForUser(
        notificationConfig: NotificationConfigEntity,
        rule: NotificationRuleEntity,
        documentChangeEvent: DocumentChangeEvent,
    ): NotificationDetails {

        val notificationDetails = NotificationDetails(
            title = rule.label,
            // body = "name and details of the entity ...", // we load this only in the frontend currently. Add here later if needed for other channels like email
            // TODO: add actionUrl for better linking
            context = EntityNotificationContext(
                entityType = rule.entityType,
                entityId = documentChangeEvent.documentId
            ),
            notificationType = rule.notificationType,
        )

        userNotificationPublisher.publish(
            channel = USER_NOTIFICATION_QUEUE,
            event = CreateUserNotificationEvent(
                userIdentifier = notificationConfig.userIdentifier,
                notificationChannelType = NotificationChannelType.APP,
                notificationRule = rule.externalIdentifier,
                details = notificationDetails,
            )
        )

//        todo refactor channelPush settings here
//        if (notificationConfig.channelPush) {
        userNotificationPublisher.publish(
            channel = USER_NOTIFICATION_QUEUE,
            event = CreateUserNotificationEvent(
                userIdentifier = notificationConfig.userIdentifier,
                notificationChannelType = NotificationChannelType.PUSH,
                notificationRule = rule.externalIdentifier,
                details = notificationDetails,
            )
        )
//        }

        if (notificationConfig.channelEmail) {
            userNotificationPublisher.publish(
                channel = USER_NOTIFICATION_QUEUE,
                event = CreateUserNotificationEvent(
                    userIdentifier = notificationConfig.userIdentifier,
                    notificationChannelType = NotificationChannelType.EMAIL,
                    notificationRule = rule.externalIdentifier,
                    details = notificationDetails,
                )
            )
        }

        return notificationDetails
    }

    /**
     * todo: move this to an separate class with extended testing
     */
    private fun _checkConditionForDocument(
        condition: NotificationConditionEntity,
        documentChangeEvent: DocumentChangeEvent
    ): Boolean {
        val currentValue = documentChangeEvent.currentVersion[condition.field]
        val conditionValue = condition.value

        return when (condition.operator) {
            "\$eq" -> {
                return when (currentValue) {
                    is String -> currentValue == conditionValue
                    is List<*> ->
                        currentValue.size == 1 && currentValue.first() == conditionValue

                    else -> false
                }
            }

            "\$nq" -> {
                return when (currentValue) {
                    is String -> currentValue != conditionValue
                    is List<*> ->
                        currentValue.size == 1 && currentValue.first() != conditionValue

                    else -> false
                }
            }

            "\$elemMatch" -> {
                return when (currentValue) {
                    is String -> currentValue == conditionValue
                    is List<*> -> currentValue.contains(conditionValue)
                    else -> false
                }
            }

            "\$gt" -> {
                return when (currentValue) {
                    is Number -> {
                        if (!StringUtils.isNumeric(conditionValue)) {
                            return false
                        } else {
                            currentValue.toFloat() < conditionValue.toFloat()
                        }
                    }

                    else -> false
                }
            }

            "\$gte" -> {
                return when (currentValue) {
                    is Number -> {
                        if (!StringUtils.isNumeric(conditionValue)) {
                            return false
                        } else {
                            currentValue.toFloat() <= conditionValue.toFloat()
                        }
                    }

                    else -> false
                }
            }

            "\$lt" -> {
                return when (currentValue) {
                    is Number -> {
                        if (!StringUtils.isNumeric(conditionValue)) {
                            return false
                        } else {
                            currentValue.toFloat() > conditionValue.toFloat()
                        }
                    }

                    else -> false
                }
            }

            "\$lte" -> {
                return when (currentValue) {
                    is Number -> {
                        if (!StringUtils.isNumeric(conditionValue)) {
                            return false
                        } else {
                            currentValue.toFloat() >= conditionValue.toFloat()
                        }
                    }

                    else -> false
                }
            }

            else -> {
                logger.warn("Unknown condition operator: ${condition.operator}")
                true
            }
        }
    }
}
