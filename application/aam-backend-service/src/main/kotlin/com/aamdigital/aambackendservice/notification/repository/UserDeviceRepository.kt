package com.aamdigital.aambackendservice.notification.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import java.util.*

interface UserDeviceRepository {
    fun findByUserIdentifier(
        userIdentifier: String,
        pageable: Pageable
    ): Page<UserDeviceEntity>

    fun findByDeviceToken(deviceToken: String): Optional<UserDeviceEntity>

    fun existsByDeviceToken(deviceToken: String): Boolean

    /** Does nothing if the device is not registered (any more). */
    fun deleteByDeviceToken(deviceToken: String)

    /**
     * Registers a new device. A device token can only be registered once, across all users; saving
     * a token that is already registered fails with [DeviceAlreadyRegisteredException].
     */
    fun save(userDevice: UserDeviceEntity)
}

/** The device token is already registered, possibly by another user. */
class DeviceAlreadyRegisteredException(
    cause: Throwable? = null
) : RuntimeException("The device is already registered.", cause)
