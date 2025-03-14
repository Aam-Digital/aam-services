package com.aamdigital.aambackendservice.notification.core.create.app

import com.aamdigital.aambackendservice.couchdb.core.CouchDbClient
import com.aamdigital.aambackendservice.domain.UpdateMetadata
import com.aamdigital.aambackendservice.notification.core.CreateUserNotificationEvent
import com.aamdigital.aambackendservice.notification.core.create.CreateNotificationData
import com.aamdigital.aambackendservice.notification.core.create.CreateNotificationHandler
import com.aamdigital.aambackendservice.notification.domain.EntityNotificationContext
import com.aamdigital.aambackendservice.notification.domain.NotificationChannelType
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Document to be stored in CouchDB to represent a notification event shown in the frontend app.
 * --> frontend `NotificationEvent` entity
 */
data class NotificationEventDto(
    @JsonProperty("_id")
    val id: String,
    val title: String,
    val body: String?,
    val actionUrl: String?,
    val notificationType: String,
    val context: EntityNotificationContext?,
    val created: UpdateMetadata,
)

class AppCreateNotificationHandler(
    private val couchDbClient: CouchDbClient,
) : CreateNotificationHandler {

    override fun canHandle(notificationChannelType: NotificationChannelType): Boolean =
        NotificationChannelType.APP == notificationChannelType

    override fun createMessage(createUserNotificationEvent: CreateUserNotificationEvent): CreateNotificationData {

        val event = NotificationEventDto(
            id = "NotificationEvent:${createUserNotificationEvent.details.id}",
            title = createUserNotificationEvent.details.title,
            body = createUserNotificationEvent.details.body,
            actionUrl = createUserNotificationEvent.details.actionUrl,
            notificationType = createUserNotificationEvent.details.notificationType.toString(),
            context = createUserNotificationEvent.details.context,
            created = UpdateMetadata(
                at = createUserNotificationEvent.details.created.toString(),
                by = "system"
            ),
        )

        val userNotificationDb = "notifications_${createUserNotificationEvent.userIdentifier}"

        ensureUserNotificationDatabaseExists(userNotificationDb)

        couchDbClient
            .putDatabaseDocument(
                database = userNotificationDb,
                documentId = event.id,
                body = event,
            )

        return CreateNotificationData(
            success = true,
            messageCreated = false,
            messageReference = null
        )
    }

    private fun ensureUserNotificationDatabaseExists(databaseName: String) {
        val databases = couchDbClient.allDatabases()
        if (!databases.contains(databaseName)) {
            couchDbClient.createDatabase(databaseName)
        }
    }
}
