package com.aamdigital.aambackendservice.notification.domain

import com.aamdigital.aambackendservice.domain.DomainReference
import java.time.Instant

/**
 * Representation of a user device
 */
data class UserDevice(
    var id: String,
    var deviceName: String?,
    var deviceToken: String,
    var user: DomainReference,
    var createdAt: Instant,
)
