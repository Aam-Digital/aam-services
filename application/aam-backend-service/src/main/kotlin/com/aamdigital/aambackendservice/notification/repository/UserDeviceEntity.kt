package com.aamdigital.aambackendservice.notification.repository

import com.fasterxml.jackson.annotation.JsonFormat
import java.time.OffsetDateTime

data class UserDeviceEntity(
    var deviceName: String?,
    var deviceToken: String,
    var userIdentifier: String,
    /**
     * The shared ObjectMapper leaves WRITE_DATES_AS_TIMESTAMPS enabled, so without this the
     * timestamp is stored as a numeric epoch value instead of a readable ISO-8601 string.
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    var createdAt: OffsetDateTime? = null
)
