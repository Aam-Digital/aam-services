package com.aamdigital.aambackendservice.notification.core

import com.aamdigital.aambackendservice.notification.domain.NotificationChannelType
import com.aamdigital.aambackendservice.notification.domain.NotificationDetails

data class CreateUserNotificationEvent(
    val userIdentifier: String,
    val notificationChannelType: NotificationChannelType,
    val notificationRule: String,
    val details: NotificationDetails,
    /**
     * Address of a recipient without a user account (public form submission), taken from the record.
     * Nullable with a default so that outbox entries written before this field existed still deserialize.
     */
    val recipientEmail: String? = null,
    /** Subject the form's admin wrote; `null` falls back to the default wording. */
    val customSubject: String? = null,
    /** Message the form's admin wrote; `null` falls back to the default text. */
    val customBody: String? = null
)
