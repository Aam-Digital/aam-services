package com.aamdigital.aambackendservice.notification.repository

import com.aamdigital.aambackendservice.notification.domain.UserDevice

/**
 * Stores the push devices registered per user.
 *
 * Every operation is scoped to a user: both REST endpoints carry the caller's JWT and push
 * delivery looks devices up by user, so there is no access path that needs a global lookup by
 * device token.
 */
interface UserDeviceRepository {
    fun findByUserIdentifier(userIdentifier: String): List<UserDevice>

    fun findDevice(
        userIdentifier: String,
        deviceToken: String
    ): UserDevice?

    fun addDevice(
        userIdentifier: String,
        device: UserDevice
    )

    fun removeDevice(
        userIdentifier: String,
        deviceToken: String
    )
}
