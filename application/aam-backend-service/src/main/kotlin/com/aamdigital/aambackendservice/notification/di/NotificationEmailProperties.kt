package com.aamdigital.aambackendservice.notification.di

import com.aamdigital.aambackendservice.notification.ConditionalOnNotificationEmailEnabled
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("notification.email")
@ConditionalOnNotificationEmailEnabled
data class NotificationEmailProperties(
    /**
     * Sender email address for outgoing notification emails.
     *
     * Expected format: RFC-5322 compliant email address.
     * Default value: empty string (`""`).
     */
    val from: String = "",
    /**
     * Prefix prepended to outgoing notification email subjects.
     *
     * Expected format: plain string.
     * Default value: `"Aam Digital"`.
     */
    val subjectPrefix: String = "Aam Digital",
    /**
     * URL where users can manage their notification settings.
     *
     * Expected format: absolute URL.
     * Default value: empty string (`""`).
     */
    val manageSettingsUrl: String = ""
)
