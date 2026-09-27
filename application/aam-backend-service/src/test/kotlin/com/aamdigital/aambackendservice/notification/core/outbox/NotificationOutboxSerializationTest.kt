package com.aamdigital.aambackendservice.notification.core.outbox

import com.aamdigital.aambackendservice.common.couchdb.core.CouchDbClient
import com.aamdigital.aambackendservice.common.outbox.Outbox
import com.aamdigital.aambackendservice.common.rest.ObjectMapperConfiguration
import com.aamdigital.aambackendservice.notification.core.CreateUserNotificationEvent
import com.aamdigital.aambackendservice.notification.domain.EntityNotificationContext
import com.aamdigital.aambackendservice.notification.domain.NotificationChannelType
import com.aamdigital.aambackendservice.notification.domain.NotificationDetails
import com.aamdigital.aambackendservice.notification.domain.NotificationType
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpHeaders

/**
 * The drainer can only deliver what it can read back, so a notification must survive being stored
 * in the outbox with the mapper the application uses.
 */
class NotificationOutboxSerializationTest {
    private val couchDbClient = mock<CouchDbClient>()
    private val objectMapper = ObjectMapperConfiguration().objectMapper()

    private val outbox =
        Outbox(
            database = "notification-outbox",
            payloadType = CreateUserNotificationEvent::class,
            couchDbClient = couchDbClient,
            couchDbInitializer = mock(),
            objectMapper = objectMapper
        )

    @Test
    fun `should read back a stored notification as it was enqueued`() {
        // Given
        val event =
            CreateUserNotificationEvent(
                userIdentifier = "user-1",
                notificationChannelType = NotificationChannelType.EMAIL,
                notificationRule = "ext-1",
                details =
                    NotificationDetails(
                        notificationType = NotificationType.ENTITY_CHANGE,
                        title = "Rule 1",
                        body = "Child:1 was updated",
                        actionUrl = "/child/1",
                        context = EntityNotificationContext(entityType = "Child", entityId = "Child:1")
                    )
            )
        whenever(couchDbClient.headDatabaseDocument(any(), any())).thenReturn(HttpHeaders())
        outbox.enqueue("key-1", event)

        val body = argumentCaptor<Any>()
        verify(couchDbClient).putDatabaseDocument(eq("notification-outbox"), any(), body.capture())
        val document = objectMapper.valueToTree<ObjectNode>(body.firstValue).put("_rev", "1-abc")
        whenever(couchDbClient.getDatabaseDocumentsByPrefix(any(), any(), eq(JsonNode::class)))
            .thenReturn(listOf(document))

        // When
        val entries = outbox.fetchAll()

        // Then
        assertThat(entries.single().payload).isEqualTo(event)
    }
}
