package com.aamdigital.aambackendservice.notification.core.trigger

import com.aamdigital.aambackendservice.changes.domain.DocumentChangeEvent
import com.aamdigital.aambackendservice.domain.UseCaseOutcome
import com.aamdigital.aambackendservice.notification.core.CreateUserNotificationEvent
import com.aamdigital.aambackendservice.notification.di.NotificationQueueConfiguration.Companion.USER_NOTIFICATION_QUEUE
import com.aamdigital.aambackendservice.notification.domain.NotificationChannelType
import com.aamdigital.aambackendservice.notification.queue.UserNotificationPublisher
import com.aamdigital.aambackendservice.notification.repository.NotificationConditionEntity
import com.aamdigital.aambackendservice.notification.repository.NotificationConfigRepository
import com.aamdigital.aambackendservice.notification.repository.NotificationRuleEntity
import org.apache.commons.lang3.StringUtils
import kotlin.jvm.optionals.getOrNull

class DefaultApplyNotificationRulesUseCase(
    val notificationConfigRepository: NotificationConfigRepository,
    val userNotificationPublisher: UserNotificationPublisher,
) : ApplyNotificationRulesUseCase() {

    override fun apply(request: ApplyNotificationRulesRequest): UseCaseOutcome<ApplyNotificationRulesData> {
        val changedEntity = request.documentChangeEvent.documentId.split(":").first()
        val changeType = extractChangeType(request.documentChangeEvent)

        val notificationConfigurations = notificationConfigRepository.findAll() // todo database access optimization

        val userRuleMappings = notificationConfigurations.groupBy { it.userIdentifier }
            .map { userRuleMapping ->
                userRuleMapping.key to userRuleMapping.value.flatMap { notificationConfig ->
                    notificationConfig.notificationRules
                        .filter { it.enabled }
                        .filter { it.entityType == changedEntity }
                        .filter { it.changeType == changeType }
                }
            }.toMap()

        return applyRules(userRuleMappings, request.documentChangeEvent)
    }

    private fun extractChangeType(documentChangeEvent: DocumentChangeEvent): String {
        if (documentChangeEvent.deleted) {
//            return "deleted" // todo frontend support for this?
            return "updated"
        }

        if (documentChangeEvent.previousVersion.isEmpty()) {
            return "created"
        }

        return "updated"
    }

    private fun applyRules(
        userRoleMapping: Map<String, List<NotificationRuleEntity>>,
        documentChangeEvent: DocumentChangeEvent,
    ): UseCaseOutcome<ApplyNotificationRulesData> {
        userRoleMapping.forEach { rule ->
            appleRulesForUser(
                userIdentifier = rule.key,
                rules = rule.value,
                documentChangeEvent = documentChangeEvent
            )
        }

        return UseCaseOutcome.Success(
            ApplyNotificationRulesData(
                0 // todo useful summery here
            )
        )
    }

    private fun appleRulesForUser(
        userIdentifier: String,
        rules: List<NotificationRuleEntity>,
        documentChangeEvent: DocumentChangeEvent,
    ) {
        rules.forEach { rule ->
            logger.trace("{} -> {}", userIdentifier, rule)

            val userConfig = notificationConfigRepository.findByUserIdentifier(userIdentifier).getOrNull() ?: return

            rule.conditions.forEach { condition ->
                val conditionOutcome: Boolean = checkConditionForDocument(condition, documentChangeEvent)
                logger.trace(
                    "rule {} outcome {} for user {}",
                    rule.externalIdentifier,
                    conditionOutcome,
                    userIdentifier
                )

                if (!conditionOutcome) {
                    return
                }
            }

            userNotificationPublisher.publish(
                channel = USER_NOTIFICATION_QUEUE,
                event = CreateUserNotificationEvent(
                    userIdentifier = userIdentifier,
                    notificationChannelType = NotificationChannelType.APP,
                    notificationRule = rule.externalIdentifier
                )
            )

            if (userConfig.channelPush) {
                userNotificationPublisher.publish(
                    channel = USER_NOTIFICATION_QUEUE,
                    event = CreateUserNotificationEvent(
                        userIdentifier = userIdentifier,
                        notificationChannelType = NotificationChannelType.PUSH,
                        notificationRule = rule.externalIdentifier
                    )
                )
            }

            if (userConfig.channelEmail) {
                userNotificationPublisher.publish(
                    channel = USER_NOTIFICATION_QUEUE,
                    event = CreateUserNotificationEvent(
                        userIdentifier = userIdentifier,
                        notificationChannelType = NotificationChannelType.EMAIL,
                        notificationRule = rule.externalIdentifier
                    )
                )
            }
        }
    }

    /**
     * todo: move this to an separate class with extended testing
     */
    private fun checkConditionForDocument(
        condition: NotificationConditionEntity,
        documentChangeEvent: DocumentChangeEvent
    ): Boolean {
        val currentValue = documentChangeEvent.currentVersion[condition.field]
        val conditionValue = condition.value

        return when (condition.operator) {
            "\$eq" -> {
                return when (currentValue) {
                    is String -> currentValue == conditionValue
                    is ArrayList<*> ->
                        currentValue.size == 1 && currentValue.first() == conditionValue

                    else -> false
                }
            }

            "\$nq" -> {
                return when (currentValue) {
                    is String -> currentValue != conditionValue
                    is ArrayList<*> ->
                        currentValue.size == 1 && currentValue.first() != conditionValue

                    else -> false
                }
            }

            "\$elemMatch" -> {
                return when (currentValue) {
                    is String -> currentValue == conditionValue
                    is ArrayList<*> -> currentValue.contains(conditionValue)
                    else -> false
                }
            }

            "\$gt" -> {
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

            "\$gte" -> {
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

            "\$lt" -> {
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

            "\$lte" -> {
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

            else -> {
                logger.warn("Unknown condition operator: ${condition.operator}")
                true
            }
        }
    }
}
