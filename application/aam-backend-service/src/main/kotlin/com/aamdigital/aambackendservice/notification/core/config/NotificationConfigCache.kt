package com.aamdigital.aambackendservice.notification.core.config

import com.aamdigital.aambackendservice.common.condition.DocumentCondition
import com.aamdigital.aambackendservice.notification.domain.NotificationType

/**
 * Cached representation of one user's notification config document.
 *
 * This shape is optimized for fast in-memory rule lookup during change-event processing.
 */
data class NotificationConfigCacheEntry(
    val userIdentifier: String,
    val channelPush: Boolean,
    val channelEmail: Boolean,
    val rules: List<NotificationRuleCacheEntry>
)

/**
 * Flattened and pre-parsed notification rule entry used during rule matching.
 *
 * Each entry represents one effective change-type + condition-group combination.
 */
data class NotificationRuleCacheEntry(
    val label: String,
    val externalIdentifier: String,
    val notificationType: NotificationType,
    val entityType: String,
    val changeType: String,
    val conditions: List<DocumentCondition>,
    val enabled: Boolean
)

/**
 * Read and refresh contract for notification configuration caching.
 *
 * Implementations keep in-memory rule state synchronized with CouchDB changes.
 */
interface NotificationConfigCache {
    fun findAll(): List<NotificationConfigCacheEntry>

    fun refreshAll()

    fun refreshConfig(
        database: String,
        notificationConfigId: String,
        deleted: Boolean
    )
}
