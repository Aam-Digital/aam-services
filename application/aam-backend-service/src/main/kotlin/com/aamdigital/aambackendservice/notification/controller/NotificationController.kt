package com.aamdigital.aambackendservice.notification.controller

import com.aamdigital.aambackendservice.error.HttpErrorDto
import com.aamdigital.aambackendservice.notification.repositiory.UserDeviceEntity
import com.aamdigital.aambackendservice.notification.repositiory.UserDeviceRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.transaction.annotation.Transactional
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.io.IOException
import kotlin.jvm.optionals.getOrNull


data class DeviceRegistrationDto(
    val deviceName: String? = null,
    val deviceToken: String,
)


@RestController
@RequestMapping("/v1/notification")
@ConditionalOnProperty(
    prefix = "features.notification-api",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = false
)
@Transactional
class NotificationController(
    private val userDeviceRepository: UserDeviceRepository,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @PostMapping("/device")
    @Validated
    fun registerDevice(
        @RequestBody deviceRegistrationDto: DeviceRegistrationDto,
        authentication: Authentication,
    ): ResponseEntity<Any> {

        if (userDeviceRepository.existsByDeviceToken(deviceRegistrationDto.deviceToken)) {
            return ResponseEntity.badRequest().body(
                HttpErrorDto(
                    errorCode = "Bad Request",
                    errorMessage = "The device is already registered."
                )
            )
        }

        userDeviceRepository.save(
            UserDeviceEntity(
                userIdentifier = authentication.name,
                deviceToken = deviceRegistrationDto.deviceToken,
                deviceName = deviceRegistrationDto.deviceName,
            )
        )


        return ResponseEntity.noContent().build()
    }

    @DeleteMapping("/device/{id}")
    fun unregisterDevice(
        @PathVariable id: String,
        authentication: Authentication,
    ): ResponseEntity<Any> {
        val userDevice =
            userDeviceRepository.findByDeviceToken(id).getOrNull() ?: return ResponseEntity.notFound().build()

        if (userDevice.userIdentifier != authentication.name) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                HttpErrorDto(
                    errorCode = "Forbidden",
                    errorMessage = "Token does not belong to User",
                )
            )
        }
        
        try {
            userDeviceRepository.deleteByDeviceToken(id)
        } catch (ex: IOException) {
            logger.warn("[NotificationController.unregisterDevice()] error: {}", ex.message)
        }

        return ResponseEntity.noContent().build()
    }
}