package com.aamdigital.aambackendservice.notification.core.config

import com.aamdigital.aambackendservice.notification.domain.NotificationType
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.JsonNode

/**
 * Raw NotificationConfig document model as stored in CouchDB.
 *
 * Used for cache hydration and incremental cache refresh.
 */
data class NotificationConfigDto(
    @JsonProperty("_id") val id: String,
    @JsonProperty("_rev") val rev: String,
    val notificationRules: List<NotificationRuleDto>,
    val channels: NotificationChannelConfig?
)

/**
 * Raw notification rule structure from CouchDB before normalization into cache entries.
 */
data class NotificationRuleDto(
    val label: String,
    val notificationType: NotificationType,
    val entityType: String,
    val changeType: List<String>,
    val conditions: JsonNode,
    val enabled: Boolean
)

/**
 * Channel flags from a NotificationConfig document.
 */
data class NotificationChannelConfig(
    val push: Boolean?,
    val email: Boolean?
)
