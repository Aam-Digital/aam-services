package com.aamdigital.aambackendservice.notification.core.config

import com.aamdigital.aambackendservice.common.couchdb.core.CouchDbClient
import com.aamdigital.aambackendservice.common.condition.DocumentConditionEngine
import com.aamdigital.aambackendservice.common.domain.UseCaseOutcome
import com.aamdigital.aambackendservice.common.error.AamErrorCode
import com.aamdigital.aambackendservice.common.error.AamException
import com.aamdigital.aambackendservice.common.error.NotFoundException
import com.aamdigital.aambackendservice.notification.domain.NotificationType
import com.aamdigital.aambackendservice.notification.repository.NotificationConditionEntity
import com.aamdigital.aambackendservice.notification.repository.NotificationConfigEntity
import com.aamdigital.aambackendservice.notification.repository.NotificationConfigRepository
import com.aamdigital.aambackendservice.notification.repository.NotificationRuleEntity
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.JsonNode
import org.springframework.util.LinkedMultiValueMap
import java.util.*

data class NotificationConfigDto(
    @JsonProperty("_id") val id: String,
    @JsonProperty("_rev") val rev: String,
    val notificationRules: List<NotificationRuleDto>,
    val channels: NotificationChannelConfig?
)

data class NotificationRuleDto(
    val label: String,
    val notificationType: NotificationType,
    val entityType: String,
    val changeType: List<String>,
    val conditions: JsonNode,
    val enabled: Boolean
)

data class NotificationChannelConfig(
    val push: Boolean?,
    val email: Boolean?
)

enum class CouchDbSyncNotificationConfigErrorCode : AamErrorCode {
    INVALID_USER_IDENTIFIER,
    IO_EXCEPTION
}

/** Synchronizes one user's notification configuration document from CouchDB into relational storage. */
class CouchDbSyncNotificationConfigUseCase(
    private val couchDbClient: CouchDbClient,
    private val notificationConfigRepository: NotificationConfigRepository,
    private val documentConditionEngine: DocumentConditionEngine = DocumentConditionEngine()
) : SyncNotificationConfigUseCase() {
    override fun apply(request: SyncNotificationConfigRequest): UseCaseOutcome<SyncNotificationConfigData> {
        val userIdentifier =
            try {
                request.notificationConfigId.split(":")[1]
            } catch (ex: Exception) {
                return UseCaseOutcome.Failure(
                    errorCode = CouchDbSyncNotificationConfigErrorCode.INVALID_USER_IDENTIFIER,
                    errorMessage = ex.localizedMessage,
                    cause = ex
                )
            }

        val notificationConfig =
            try {
                couchDbClient.getDatabaseDocument(
                    database = request.notificationConfigDatabase,
                    documentId = request.notificationConfigId,
                    queryParams = LinkedMultiValueMap(),
                    kClass = NotificationConfigDto::class
                )
            } catch (
                @Suppress("SwallowedException") ex: NotFoundException
            ) {
                val currentNotificationConfigOptional =
                    notificationConfigRepository.findByUserIdentifier(
                        userIdentifier
                    )

                currentNotificationConfigOptional.ifPresent {
                    notificationConfigRepository.delete(it)
                }

                return UseCaseOutcome.Success(
                    data =
                        SyncNotificationConfigData(
                            imported = false,
                            updated = false,
                            skipped = false,
                            deleted = true,
                            message = "NotificationConfig deleted successfully."
                        )
                )
            } catch (ex: AamException) {
                return UseCaseOutcome.Failure(
                    errorCode = CouchDbSyncNotificationConfigErrorCode.IO_EXCEPTION,
                    errorMessage = ex.localizedMessage,
                    cause = ex
                )
            }

        val currentNotificationConfigOptional = notificationConfigRepository.findByUserIdentifier(userIdentifier)

        return if (currentNotificationConfigOptional.isEmpty) {
            notificationConfigRepository.save(
                NotificationConfigEntity(
                    revision = notificationConfig.rev,
                    userIdentifier = userIdentifier,
                    channelPush = notificationConfig.channels?.push ?: false,
                    channelEmail = false, // todo email support
                    notificationRules = mapToNotificationRules(notificationConfig)
                )
            )
            UseCaseOutcome.Success(
                data =
                    SyncNotificationConfigData(
                        imported = true,
                        updated = false,
                        skipped = false,
                        deleted = false,
                        message = "NotificationConfig updated successfully."
                    )
            )
        } else {
            updateNotificationConfigEntity(currentNotificationConfigOptional.get(), notificationConfig)
        }
    }

    private fun updateNotificationConfigEntity(
        notificationConfigEntity: NotificationConfigEntity,
        notificationConfig: NotificationConfigDto
    ): UseCaseOutcome<SyncNotificationConfigData> {
        try {
            notificationConfigEntity.revision = notificationConfig.rev
            notificationConfigEntity.channelPush = notificationConfig.channels?.push ?: false
            notificationConfigEntity.channelEmail = notificationConfig.channels?.email ?: false

            // delete all existing entities from database
            notificationConfigEntity.notificationRules = emptyList()
            notificationConfigRepository.save(notificationConfigEntity)

            // create new rule entities
            notificationConfigEntity.notificationRules = mapToNotificationRules(notificationConfig)
            notificationConfigRepository.save(notificationConfigEntity)
        } catch (ex: Exception) {
            return UseCaseOutcome.Failure(
                errorCode = CouchDbSyncNotificationConfigErrorCode.IO_EXCEPTION,
                errorMessage = ex.localizedMessage,
                cause = ex
            )
        }

        return UseCaseOutcome.Success(
            data =
                SyncNotificationConfigData(
                    imported = true,
                    updated = true,
                    skipped = false,
                    deleted = false,
                    message = "NotificationConfig updated successfully."
                )
        )
    }

    private fun mapToNotificationRules(notificationConfig: NotificationConfigDto): List<NotificationRuleEntity> =
        notificationConfig.notificationRules.flatMap { rule ->
            val conditionGroups =
                documentConditionEngine.parseConditionGroups(rule.conditions).map { conditions ->
                    conditions.map {
                        NotificationConditionEntity(
                            field = it.field,
                            operator = it.operator,
                            value = it.value
                        )
                    }
                }
            rule.changeType.flatMap { changeType ->
                conditionGroups.map { conditions ->
                    NotificationRuleEntity(
                        notificationType = rule.notificationType,
                        label = rule.label,
                        externalIdentifier = UUID.randomUUID().toString(),
                        entityType = rule.entityType,
                        changeType = changeType,
                        enabled = rule.enabled,
                        conditions = conditions
                    )
                }
            }
        }
}
