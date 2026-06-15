package com.aamdigital.aambackendservice.notification

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty

/**
 * Activates the annotated bean only when the notification email feature flag is enabled.
 * Single source of truth for the `features.notification-api.email.enabled` property.
 *
 * Typically stacked with [ConditionalOnNotificationApiEnabled].
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@ConditionalOnProperty(
    prefix = "features.notification-api.email",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = false,
)
annotation class ConditionalOnNotificationEmailEnabled
