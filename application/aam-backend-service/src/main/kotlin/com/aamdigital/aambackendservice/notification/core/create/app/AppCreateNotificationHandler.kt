package com.aamdigital.aambackendservice.notification.core.create.app

import com.aamdigital.aambackendservice.couchdb.core.CouchDbClient
import com.aamdigital.aambackendservice.notification.core.create.CreateNotificationData
import com.aamdigital.aambackendservice.notification.core.create.CreateNotificationHandler
import com.aamdigital.aambackendservice.notification.core.CreateUserNotificationEvent
import com.aamdigital.aambackendservice.notification.domain.NotificationChannelType
import com.aamdigital.aambackendservice.notification.domain.NotificationType
import com.aamdigital.aambackendservice.notification.repository.NotificationConfigRepository
import com.fasterxml.jackson.annotation.JsonProperty
import java.util.*
import kotlin.jvm.optionals.getOrNull

data class NotificationEventDto(
    @JsonProperty("_id")
    val id: String,
    val title: String,
    val body: String,
    val actionUrl: String,
    val notificationFor: String,
    val notificationType: String,
    val created: TimeStampDto,
    val updated: TimeStampDto,
)

data class TimeStampDto(
    val at: String,
    val by: String,
)

class AppCreateNotificationHandler(
    private val couchDbClient: CouchDbClient,
    private val notificationConfigRepository: NotificationConfigRepository,
) : CreateNotificationHandler {

    override fun canHandle(notificationChannelType: NotificationChannelType): Boolean =
        NotificationChannelType.APP == notificationChannelType

    override fun createMessage(createUserNotificationEvent: CreateUserNotificationEvent): CreateNotificationData {

        val notificationConfig = notificationConfigRepository.findByUserIdentifier(
            userIdentifier = createUserNotificationEvent.userIdentifier
        ).getOrNull()

        if (notificationConfig == null) {
            return CreateNotificationData(
                success = true,
                messageCreated = false,
                messageReference = null
            )
        }

        val notificationRule = notificationConfig.notificationRules.find {
            it.externalIdentifier == createUserNotificationEvent.notificationRule
        }

        if (notificationRule == null) {
            return CreateNotificationData(
                success = true,
                messageCreated = false,
                messageReference = null
            )
        }


        val event = NotificationEventDto(
            id = "NotificationEvent:${UUID.randomUUID()}",
            title = "Update from Aam Digital",
            body = notificationRule.label,
            actionUrl = "",
            notificationFor = createUserNotificationEvent.userIdentifier,
            notificationType = NotificationType.ENTITY_CHANGE.toString(),
            created = TimeStampDto(
                at = "",
                by = "system"
            ),
            updated = TimeStampDto(
                at = "",
                by = "system"
            )
        )

        couchDbClient
            .putDatabaseDocument(
                database = "app", // todo get user database here
                documentId = event.id,
                body = event,
            )

        return CreateNotificationData(
            success = true,
            messageCreated = false,
            messageReference = null
        )
    }
}
