package com.aamdigital.aambackendservice.notification.core.config

import com.aamdigital.aambackendservice.common.condition.DocumentConditions
import com.aamdigital.aambackendservice.notification.domain.NotificationType

/**
 * Cached representation of one config document that notification rules are derived from.
 *
 * This shape is optimized for fast in-memory rule lookup during change-event processing.
 */
data class NotificationConfigCacheEntry(
    val userIdentifier: String,
    val channelPush: Boolean,
    val channelEmail: Boolean,
    val rules: List<NotificationRuleCacheEntry>,
    /** set for recipients without a user account: the field of the changed document holding their address */
    val recipientEmailField: String? = null
) {
    val isExternalRecipient: Boolean get() = recipientEmailField != null
}

/**
 * Flattened and pre-parsed notification rule entry used during rule matching.
 *
 * Each entry represents one change type of a rule, with the rule's complete condition tree.
 */
data class NotificationRuleCacheEntry(
    val label: String,
    val externalIdentifier: String,
    val notificationType: NotificationType,
    val entityType: String,
    val changeType: String,
    val conditions: DocumentConditions,
    val enabled: Boolean
)

/**
 * Read and refresh contract for notification configuration caching.
 *
 * Implementations keep in-memory rule state synchronized with CouchDB changes.
 */
interface NotificationConfigCache {
    /** entity type of the documents the rules are derived from; a change to one refreshes the cache */
    val sourceEntityType: String

    fun findAll(): List<NotificationConfigCacheEntry>

    fun refreshConfig(
        database: String,
        notificationConfigId: String,
        deleted: Boolean
    )
}
