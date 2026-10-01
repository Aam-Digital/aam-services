package com.aamdigital.aambackendservice.notification.core.create.app

import com.aamdigital.aambackendservice.common.couchdb.core.CouchDbClient
import com.aamdigital.aambackendservice.common.couchdb.core.DefaultCouchDbClient.DefaultCouchDbClientErrorCode
import com.aamdigital.aambackendservice.common.couchdb.dto.DocSuccess
import com.aamdigital.aambackendservice.common.error.ExternalSystemException
import com.aamdigital.aambackendservice.notification.core.CreateUserNotificationEvent
import com.aamdigital.aambackendservice.notification.core.create.CreateNotificationData
import com.aamdigital.aambackendservice.notification.domain.NotificationChannelType
import com.aamdigital.aambackendservice.notification.domain.NotificationDetails
import com.aamdigital.aambackendservice.notification.domain.NotificationType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.springframework.http.HttpHeaders

class AppCreateNotificationHandlerTest {
    private val couchDbClient = mock<CouchDbClient>()
    private val handler = AppCreateNotificationHandler(couchDbClient = couchDbClient)

    private val event =
        CreateUserNotificationEvent(
            userIdentifier = "user-1",
            notificationChannelType = NotificationChannelType.APP,
            notificationRule = "rule-1",
            details = NotificationDetails(notificationType = NotificationType.ENTITY_CHANGE, title = "Child added")
        )

    private val userDatabase = "notifications_user-1"
    private val documentId = "NotificationEvent:${event.details.id}"
    private val written = DocSuccess(ok = true, id = documentId, rev = "1-a")

    private fun stubDelivered(eTag: String?) {
        val headers = HttpHeaders()
        eTag?.let { headers.eTag = it }
        whenever(couchDbClient.headDatabaseDocument(userDatabase, documentId)).thenReturn(headers)
    }

    @Test
    fun `should write the notification without checking the database first`() {
        // Given
        stubDelivered(eTag = null)
        whenever(couchDbClient.putDatabaseDocument(eq(userDatabase), eq(documentId), any())).thenReturn(written)

        // When
        val result = handler.createMessage(event)

        // Then
        assertThat(result)
            .isEqualTo(CreateNotificationData(success = true, messageCreated = false, messageReference = null))
        verify(couchDbClient).headDatabaseDocument(userDatabase, documentId)
        verify(couchDbClient).putDatabaseDocument(
            eq(userDatabase),
            eq(documentId),
            argThat<NotificationEventDto> { title == "Child added" && created.by == "system" }
        )
        verifyNoMoreInteractions(couchDbClient)
    }

    @Test
    fun `should create the user's notification database when the first notification finds it missing`() {
        // Given
        stubDelivered(eTag = null)
        whenever(couchDbClient.putDatabaseDocument(eq(userDatabase), eq(documentId), any()))
            .thenAnswer { throw ExternalSystemException(code = DefaultCouchDbClientErrorCode.DATABASE_NOT_FOUND) }
            .thenReturn(written)

        // When
        val result = handler.createMessage(event)

        // Then
        assertThat(result.success).isTrue()
        verify(couchDbClient).createDatabase(userDatabase)
        verify(couchDbClient, times(2)).putDatabaseDocument(eq(userDatabase), eq(documentId), any())
    }

    @Test
    fun `should leave an already delivered notification alone`() {
        // Given it is in the user's database already, where writing it again would give it a fresh timestamp
        stubDelivered(eTag = "\"1-a\"")

        // When
        val result = handler.createMessage(event)

        // Then
        assertThat(result.messageReference).isEqualTo(documentId)
        verify(couchDbClient, never()).putDatabaseDocument(any(), any(), any())
        verify(couchDbClient, never()).createDatabase(any())
    }
}
