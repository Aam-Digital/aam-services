package com.aamdigital.aambackendservice.notification.core.create.push

import com.aamdigital.aambackendservice.notification.core.CreateUserNotificationEvent
import com.aamdigital.aambackendservice.notification.di.NotificationFirebaseClientConfiguration
import com.aamdigital.aambackendservice.notification.domain.EntityNotificationContext
import com.aamdigital.aambackendservice.notification.domain.NotificationChannelType
import com.aamdigital.aambackendservice.notification.domain.NotificationDetails
import com.aamdigital.aambackendservice.notification.domain.NotificationType
import com.aamdigital.aambackendservice.notification.repository.UserDeviceEntity
import com.aamdigital.aambackendservice.notification.repository.UserDeviceRepository
import com.google.firebase.messaging.BatchResponse
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.SendResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageImpl

@ExtendWith(MockitoExtension::class)
class PushCreateNotificationHandlerTest {

    private lateinit var handler: PushCreateNotificationHandler

    @Mock
    lateinit var firebaseMessaging: FirebaseMessaging

    @Mock
    lateinit var userDeviceRepository: UserDeviceRepository

    @Mock
    lateinit var batchResponse: BatchResponse

    @Mock
    lateinit var sendResponse: SendResponse

    private val config = NotificationFirebaseClientConfiguration(
        credentialFileBase64 = "",
        linkBaseUrl = "https://app.test"
    )

    private val notificationEvent = CreateUserNotificationEvent(
        userIdentifier = "test-user",
        notificationChannelType = NotificationChannelType.PUSH,
        notificationRule = "test-rule",
        details = NotificationDetails(
            notificationType = NotificationType.ENTITY_CHANGE,
            title = "A new record was added",
            context = EntityNotificationContext(entityType = "Child", entityId = "Child:1")
        )
    )

    @BeforeEach
    fun setUp() {
        handler = PushCreateNotificationHandler(
            firebaseMessaging = firebaseMessaging,
            userDeviceRepository = userDeviceRepository,
            notificationFirebaseClientConfiguration = config
        )
    }

    @Test
    fun `canHandle returns true for PUSH channel type`() {
        assertThat(handler.canHandle(NotificationChannelType.PUSH)).isTrue()
    }

    @Test
    fun `canHandle returns false for non-PUSH channel type`() {
        assertThat(handler.canHandle(NotificationChannelType.APP)).isFalse()
        assertThat(handler.canHandle(NotificationChannelType.EMAIL)).isFalse()
    }

    @Test
    fun `should return success without sending when user has no registered devices`() {
        // Given
        whenever(userDeviceRepository.findByUserIdentifier(any(), any()))
            .thenReturn(PageImpl(emptyList()))

        // When
        val result = handler.createMessage(notificationEvent)

        // Then
        assertThat(result.success).isTrue()
        assertThat(result.messageCreated).isFalse()
        assertThat(result.messageReference).isNull()
        verifyNoInteractions(firebaseMessaging)
    }

    @Test
    fun `should send push notification when user has registered devices`() {
        // Given
        val device = UserDeviceEntity(
            id = 1L,
            deviceName = "My Phone",
            deviceToken = "device-token-abc",
            userIdentifier = "test-user"
        )
        whenever(userDeviceRepository.findByUserIdentifier(any(), any()))
            .thenReturn(PageImpl(listOf(device)))
        whenever(sendResponse.messageId).thenReturn("firebase-msg-id-1")
        whenever(batchResponse.responses).thenReturn(listOf(sendResponse))
        whenever(firebaseMessaging.sendEachForMulticast(any())).thenReturn(batchResponse)

        // When
        val result = handler.createMessage(notificationEvent)

        // Then
        assertThat(result.success).isTrue()
        assertThat(result.messageCreated).isTrue()
        assertThat(result.messageReference).contains("firebase-msg-id-1")
        verify(firebaseMessaging).sendEachForMulticast(any())
    }

    @Test
    fun `should include all device tokens when user has multiple registered devices`() {
        // Given
        val devices = listOf(
            UserDeviceEntity(id = 1L, deviceName = "Phone", deviceToken = "token-1", userIdentifier = "test-user"),
            UserDeviceEntity(id = 2L, deviceName = "Tablet", deviceToken = "token-2", userIdentifier = "test-user")
        )
        whenever(userDeviceRepository.findByUserIdentifier(any(), any()))
            .thenReturn(PageImpl(devices))
        whenever(sendResponse.messageId).thenReturn("msg-id")
        whenever(batchResponse.responses).thenReturn(listOf(sendResponse, sendResponse))
        whenever(firebaseMessaging.sendEachForMulticast(any())).thenReturn(batchResponse)

        // When
        val result = handler.createMessage(notificationEvent)

        // Then
        assertThat(result.success).isTrue()
        assertThat(result.messageCreated).isTrue()
        verify(firebaseMessaging).sendEachForMulticast(any())
    }
}
