package com.aamdigital.aambackendservice.notification.controller

import com.aamdigital.aambackendservice.notification.repositiory.UserDeviceRepository
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.MulticastMessage
import com.google.firebase.messaging.Notification
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.domain.Pageable
import org.springframework.http.ResponseEntity
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class TestMessageResponse(
    val receiverIds: List<String>,
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
    private val firebaseMessaging: FirebaseMessaging,
    private val userDeviceRepository: UserDeviceRepository,
) {

    @PostMapping("/message/device-test")
    fun sendTestMessageToDevice(
        authentication: JwtAuthenticationToken,
    ): ResponseEntity<TestMessageResponse> {
        val userDevices =
            userDeviceRepository.findByUserIdentifier(
                authentication.name ?: authentication.tokenAttributes["username"].toString(), Pageable.unpaged()
            )
                .map {
                    it.deviceToken
                }.toList()

        if (userDevices.isEmpty()) {
            return ResponseEntity.ok(
                TestMessageResponse(
                    receiverIds = emptyList()
                )
            )
        }

        val message = MulticastMessage.builder()
            .addAllTokens(userDevices)
            .setNotification(Notification.builder().setTitle("Aam Digital Test").setBody("Hello World").build())
            .build()

        val response = firebaseMessaging.sendEachForMulticast(message)

        val ids = response.responses.map { it.messageId }.toList()

        return ResponseEntity.ok().body(
            TestMessageResponse(
                receiverIds = ids
            )
        )
    }
}
