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
    val manageSettingsUrl: String = "",
    /**
     * Language used for the boilerplate text of outgoing notification emails.
     *
     * Selects the bundled (or mounted override) email template under
     * `{locale}/notification/...`. A region suffix is ignored (`de-DE` -> `de`).
     *
     * NOTE: This is a single, deployment-wide default and is independent of each
     * user's UI language. Operators should keep it in sync with the site default
     * language (`SiteSettings.defaultLanguage`); otherwise emails may be sent in a
     * different language than the app UI.
     *
     * Expected format: language code such as `en`, `de`, `fr`.
     * Default value: `"en"`.
     */
    val locale: String = "en"
)
