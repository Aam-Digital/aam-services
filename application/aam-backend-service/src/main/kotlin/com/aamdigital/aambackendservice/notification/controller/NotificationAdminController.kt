package com.aamdigital.aambackendservice.notification.controller

import com.aamdigital.aambackendservice.notification.repositiory.UserDeviceRepository
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.MulticastMessage
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.domain.Pageable
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
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
        authentication: Authentication,
    ): ResponseEntity<TestMessageResponse> {
        val userDevices =
            userDeviceRepository.findByUserIdentifier(authentication.name, Pageable.unpaged())
                .map {
                    it.userIdentifier
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
            .putData("body", "Hello World")
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
