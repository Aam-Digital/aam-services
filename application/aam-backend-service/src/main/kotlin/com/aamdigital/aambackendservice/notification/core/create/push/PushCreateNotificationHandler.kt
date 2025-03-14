package com.aamdigital.aambackendservice.notification.core.create.push

import com.aamdigital.aambackendservice.notification.core.CreateUserNotificationEvent
import com.aamdigital.aambackendservice.notification.core.create.CreateNotificationData
import com.aamdigital.aambackendservice.notification.core.create.CreateNotificationHandler
import com.aamdigital.aambackendservice.notification.domain.NotificationChannelType
import com.aamdigital.aambackendservice.notification.repository.UserDeviceRepository
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.MulticastMessage
import com.google.firebase.messaging.Notification
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service

@Service
class PushCreateNotificationHandler(
    private val firebaseMessaging: FirebaseMessaging,
    private val userDeviceRepository: UserDeviceRepository,
) : CreateNotificationHandler {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun canHandle(notificationChannelType: NotificationChannelType): Boolean =
        NotificationChannelType.PUSH == notificationChannelType

    override fun createMessage(createUserNotificationEvent: CreateUserNotificationEvent): CreateNotificationData {
        val userDevices = userDeviceRepository.findByUserIdentifier(
            createUserNotificationEvent.userIdentifier, Pageable.unpaged()
        ).map {
            it.deviceToken
        }.toList()

        if (userDevices.isEmpty()) {
            return CreateNotificationData(
                success = true,
                messageCreated = false,
                messageReference = null
            )
        }

        val message = MulticastMessage.builder().addAllTokens(userDevices)
            .setNotification(
                Notification.builder()
                    .setTitle("Update from Aam Digital")
                    .setBody(createUserNotificationEvent.details.title)
                    .build()
            ).build()

        val response = firebaseMessaging.sendEachForMulticast(message)

        val ids = response.responses.map { it.messageId }.toList()

        logger.trace("push notification sent {}", ids.toString())

        return CreateNotificationData(
            success = true, messageCreated = true, messageReference = ids.toString()
        )
    }
}
