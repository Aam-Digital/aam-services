package com.aamdigital.aambackendservice.notification.core.config

import com.aamdigital.aambackendservice.notification.domain.NotificationType
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
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

/** The parts of a `PublicFormConfig` document (ndb-core entity) that configure the confirmation email. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class PublicFormConfigDto(
    @JsonProperty("_id") val id: String,
    /** a plain string, or an object keyed by language code */
    val title: JsonNode?,
    /** the record type the form creates */
    val entity: String?,
    val sendConfirmationEmail: Boolean?,
    val confirmationEmailField: String?
)
