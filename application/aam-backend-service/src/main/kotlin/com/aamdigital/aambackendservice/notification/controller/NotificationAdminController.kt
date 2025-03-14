package com.aamdigital.aambackendservice.notification.controller

import com.aamdigital.aambackendservice.notification.core.CreateUserNotificationEvent
import com.aamdigital.aambackendservice.notification.core.create.CreateNotificationData
import com.aamdigital.aambackendservice.notification.core.create.push.PushCreateNotificationHandler
import com.aamdigital.aambackendservice.notification.domain.NotificationChannelType
import com.aamdigital.aambackendservice.notification.domain.NotificationDetails
import com.aamdigital.aambackendservice.notification.domain.NotificationType
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.ResponseEntity
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class TestMessageResponse(
    val outcome: CreateNotificationData,
)

@RestController
@RequestMapping("/v1/notification")
@ConditionalOnProperty(
    prefix = "features.notification-api",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = false
)
class NotificationAdminController(
    private val pushCreateNotificationHandler: PushCreateNotificationHandler,
) {

    @PostMapping("/message/device-test")
    fun sendTestMessageToDevice(
        authentication: JwtAuthenticationToken,
    ): ResponseEntity<TestMessageResponse> {

        val testEvent = CreateUserNotificationEvent(
            userIdentifier = authentication.name ?: authentication.tokenAttributes["username"].toString(),
            notificationChannelType = NotificationChannelType.PUSH,
            notificationRule = "test",
            details = NotificationDetails(
                title = "Test Notification",
                body = "Hello World",
                notificationType = NotificationType.UNKNOWN,
            )
        )

        val outcome = pushCreateNotificationHandler.createMessage(testEvent)

        return ResponseEntity.ok().body(
            TestMessageResponse(outcome)
        )
    }
}
