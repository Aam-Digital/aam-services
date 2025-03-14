package com.aamdigital.aambackendservice.notification.domain

import java.time.Instant
import java.util.*

/**
 * Details of a Notification Event containing relevant information for a user.
 * All CreateNotificationHandlers can base their actions on this data without having to regenerate similar human-readable data.
 */
data class NotificationDetails(
    val id: UUID = UUID.randomUUID(),
    val notificationType: NotificationType,
    val title: String,
    val body: String? = null,
    val actionUrl: String = "",
    val context: EntityNotificationContext? = null,
    val created: Instant = Instant.now(),
)

/**
 * --> frontend `EntityNotificationContext`
 */
data class EntityNotificationContext(
    val entityType: String,
    val entityId: String?,
)
