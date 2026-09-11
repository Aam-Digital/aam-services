package com.aamdigital.aambackendservice.notification.domain

import com.fasterxml.jackson.annotation.JsonFormat
import java.time.Instant

/**
 * One device a user registered for push notifications.
 *
 * The [deviceToken] is minted by Firebase on the client and cannot be reconstructed server-side,
 * which makes it the only push state that has to survive a restart.
 */
data class UserDevice(
    val deviceToken: String,
    val deviceName: String?,
    /**
     * The shared ObjectMapper leaves WRITE_DATES_AS_TIMESTAMPS enabled, so without this an Instant
     * is stored as a numeric epoch value instead of a readable ISO-8601 string.
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    val createdAt: Instant? = null
)
